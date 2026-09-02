package io.ztoken.portal.console;

import io.ztoken.portal.session.NewApiIdentity;
import io.ztoken.portal.session.PortalSessionService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("log-controller-mock-server")
class LogControllerTest {

    private static final MockWebServer NEW_API = startServer();

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private PortalSessionService sessions;

    @AfterAll
    static void stopNewApi() throws Exception {
        NEW_API.shutdown();
    }

    @DynamicPropertySource
    static void newApiProperties(DynamicPropertyRegistry registry) {
        registry.add("portal.new-api.base-url", () -> NEW_API.url("/").toString());
    }

    @Test
    void listCapsPageSizeEncodesFiltersAndReturnsOnlySafeFields() throws Exception {
        NEW_API.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("""
                        {"success":true,"data":{"page":1,"page_size":50,"total":1,"items":[
                        {"id":42,"created_at":1710000000,"type":2,"content":"completed","token_name":"app-key",
                        "model_name":"GPT 4 & test","quota":120,"prompt_tokens":100,"completion_tokens":20,
                        "use_time":50,"is_stream":true,"request_id":"req-1","channel":99,
                        "channel_name":"private-channel","other":"{\\"admin_info\\":{\\"secret\\":true}}"}
                        ]}}
                        """));
        String sessionId = session("access-token");

        URI uri = UriComponentsBuilder.fromUriString(http.getRootUri()).path("/api/console/logs")
                .queryParam("page", 1).queryParam("pageSize", 999)
                .queryParam("modelName", "GPT 4 & test").queryParam("tokenName", "app key")
                .queryParam("type", 2).build().encode().toUri();
        ResponseEntity<LogPage> response = http.exchange(uri, HttpMethod.GET, authed(sessionId), LogPage.class);

        RecordedRequest upstream = NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items()).containsExactly(
                new LogEntry(42L, 1710000000L, 2, "completed", "app-key", "GPT 4 & test", 120L,
                        100L, 20L, 50L, true, "req-1"));
        assertThat(upstream.getRequestUrl().queryParameter("page_size")).isEqualTo("50");
        assertThat(upstream.getRequestUrl().queryParameter("model_name")).isEqualTo("GPT 4 & test");
        assertThat(upstream.getRequestUrl().queryParameter("token_name")).isEqualTo("app key");
        assertThat(upstream.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-token");
        assertThat(upstream.getHeader("New-Api-User")).isEqualTo("7");
    }

    @Test
    void statsForwardsOnlySupportedFiltersAndMapsMetrics() throws Exception {
        NEW_API.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":{\"quota\":120,\"rpm\":3,\"tpm\":1500}}"));
        String sessionId = session("access-token");

        ResponseEntity<LogStats> response = http.exchange(
                "/api/console/logs/stats?startTimestamp=1710000000&endTimestamp=1710086400&modelName=gpt-4o&type=2",
                HttpMethod.GET, authed(sessionId), LogStats.class);

        RecordedRequest upstream = NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new LogStats(120L, 3L, 1500L));
        assertThat(upstream.getPath()).startsWith("/api/log/self/stat?");
        assertThat(upstream.getRequestUrl().queryParameter("p")).isNull();
        assertThat(upstream.getRequestUrl().queryParameter("page_size")).isNull();
        assertThat(upstream.getRequestUrl().queryParameter("model_name")).isEqualTo("gpt-4o");
    }

    @Test
    void emptyLogListIsAValidEmptyPage() throws Exception {
        NEW_API.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":{\"page\":1,\"page_size\":50,\"total\":0,\"items\":[]}}"));
        String sessionId = session("access-token");

        ResponseEntity<LogPage> response = http.exchange("/api/console/logs", HttpMethod.GET,
                authed(sessionId), LogPage.class);

        NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items()).isEmpty();
        assertThat(response.getBody().total()).isZero();
    }

    @Test
    void upstreamBusinessFailureReturnsSafeGatewayError() throws Exception {
        NEW_API.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":false,\"message\":\"internal upstream detail\"}"));
        String sessionId = session("access-token");

        ResponseEntity<Map> response = http.exchange("/api/console/logs", HttpMethod.GET,
                authed(sessionId), Map.class);

        NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).containsEntry("code", "NEWAPI_ERROR");
        assertThat(response.getBody().get("message").toString()).doesNotContain("internal upstream detail");
    }

    private String session(String accessToken) {
        return sessions.create(new NewApiIdentity(7L, "alice"), accessToken).getId();
    }

    private HttpEntity<Void> authed(String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "PORTAL_SESSION=" + sessionId);
        return new HttpEntity<>(headers);
    }

    private static MockWebServer startServer() {
        MockWebServer server = new MockWebServer();
        try {
            server.start();
            return server;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
