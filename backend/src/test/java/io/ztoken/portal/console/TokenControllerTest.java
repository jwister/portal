package io.ztoken.portal.console;

import io.ztoken.portal.session.NewApiIdentity;
import io.ztoken.portal.session.PortalSessionService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenControllerTest {

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
    void listReturnsOnlySafeFieldsForTheCurrentUser() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("""
                        {"success":true,"data":{"page":1,"page_size":100,"total":1,"items":[
                        {"id":3,"user_id":7,"name":"server","status":1,"remain_quota":500,"used_quota":20,
                        "unlimited_quota":false,"expired_time":-1,"key":"sk-abcd********wxyz",
                        "model_limits":"{\\"gpt\\":10}","auto_groups":["default"]}
                        ]}}
                        """));
        String sessionId = session("access-token");

        ResponseEntity<TokenList> response = http.exchange("/api/console/tokens", HttpMethod.GET,
                authed(sessionId), TokenList.class);

        RecordedRequest upstream = NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items()).containsExactly(
                new TokenSummary(3L, "server", true, 500L, 20L, false, -1L, "sk-abcd********wxyz"));
        assertThat(upstream.getPath()).isEqualTo("/api/token/?p=0&page_size=100");
        assertThat(upstream.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-token");
        assertThat(upstream.getHeader("New-Api-User")).isEqualTo("7");
    }

    @Test
    void createForwardsSessionHeadersAndReturnsSuccessWithoutKey() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"message\":\"\"}"));
        String sessionId = session("access-token");

        ResponseEntity<String> response = http.exchange("/api/console/tokens", HttpMethod.POST,
                authedJson(sessionId, """
                        {"name":"app-key","unlimited":true,"remainingQuota":0,"expiredTime":-1}
                        """), String.class);

        RecordedRequest upstream = NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(upstream.getMethod()).isEqualTo("POST");
        assertThat(upstream.getPath()).isEqualTo("/api/token/");
        assertThat(upstream.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-token");
        assertThat(upstream.getHeader("New-Api-User")).isEqualTo("7");
        assertThat(upstream.getBody().readUtf8())
                .contains("\"name\":\"app-key\"")
                .contains("\"unlimited_quota\":true");
    }

    @Test
    void updateForwardsIdFromPathAndReturnsMaskedToken() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"message\":\"\",\"data\":{\"id\":3,\"name\":\"renamed\","
                        + "\"status\":1,\"remain_quota\":100,\"used_quota\":0,\"expired_time\":-1,"
                        + "\"key\":\"sk-abcd********wxyz\"}}"));
        String sessionId = session("access-token");

        ResponseEntity<TokenSummary> response = http.exchange("/api/console/tokens/3", HttpMethod.PUT,
                authedJson(sessionId, """
                        {"name":"renamed","unlimited":false,"remainingQuota":100,"expiredTime":-1}
                        """), TokenSummary.class);

        RecordedRequest upstream = NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(
                new TokenSummary(3L, "renamed", true, 100L, 0L, false, -1L, "sk-abcd********wxyz"));
        assertThat(upstream.getMethod()).isEqualTo("PUT");
        assertThat(upstream.getPath()).isEqualTo("/api/token/");
        assertThat(upstream.getBody().readUtf8()).contains("\"id\":3").contains("\"name\":\"renamed\"");
    }

    @Test
    void enableDisableMapsToStatusOnlyUpdate() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"message\":\"\",\"data\":{\"id\":3,\"name\":\"server\","
                        + "\"status\":2,\"remain_quota\":500,\"used_quota\":0,\"expired_time\":-1,"
                        + "\"key\":\"sk-abcd********wxyz\"}}"));
        String sessionId = session("access-token");

        ResponseEntity<TokenSummary> response = http.exchange("/api/console/tokens/3/status", HttpMethod.PUT,
                authedJson(sessionId, "{\"enabled\":false}"), TokenSummary.class);

        RecordedRequest upstream = NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().enabled()).isFalse();
        assertThat(upstream.getMethod()).isEqualTo("PUT");
        assertThat(upstream.getPath()).isEqualTo("/api/token/?status_only=true");
        assertThat(upstream.getBody().readUtf8()).contains("\"id\":3").contains("\"status\":2");
    }

    @Test
    void deleteRemovesTheToken() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"message\":\"\"}"));
        String sessionId = session("access-token");

        ResponseEntity<String> response = http.exchange("/api/console/tokens/3", HttpMethod.DELETE,
                authed(sessionId), String.class);

        RecordedRequest upstream = NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(upstream.getMethod()).isEqualTo("DELETE");
        assertThat(upstream.getPath()).isEqualTo("/api/token/3");
        assertThat(upstream.getHeader("New-Api-User")).isEqualTo("7");
    }

    @Test
    void fullKeyReturnsPlaintextOnlyFromExplicitKeyEndpoint() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":{\"key\":\"sk-plain-123\"}}"));
        String sessionId = session("access-token");

        ResponseEntity<TokenKey> response = http.exchange("/api/console/tokens/3/key", HttpMethod.GET,
                authed(sessionId), TokenKey.class);

        RecordedRequest upstream = NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().key()).isEqualTo("sk-plain-123");
        assertThat(upstream.getMethod()).isEqualTo("POST");
        assertThat(upstream.getPath()).isEqualTo("/api/token/3/key");
    }

    @Test
    void usageIsReportedAsNotSupportedWithoutCallingUpstream() throws Exception {
        int requestCountBefore = NEW_API.getRequestCount();
        String sessionId = session("access-token");

        ResponseEntity<Map> response = http.exchange("/api/console/tokens/3/usage", HttpMethod.GET,
                authed(sessionId), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        assertThat(response.getBody()).containsEntry("code", "NOT_SUPPORTED");
        assertThat(NEW_API.getRequestCount()).isEqualTo(requestCountBefore);
    }

    @Test
    void upstreamBusinessFailureReturnsSafeErrorWithoutLeakingBody() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":false,\"message\":\"internal secret detail\"}"));
        String sessionId = session("access-token");

        ResponseEntity<Map> response = http.exchange("/api/console/tokens/3", HttpMethod.PUT,
                authedJson(sessionId, """
                        {"name":"x","unlimited":false,"remainingQuota":0,"expiredTime":-1}
                        """), Map.class);

        NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).containsEntry("code", "NEWAPI_ERROR");
        assertThat(response.getBody().get("message").toString()).doesNotContain("secret detail");
    }

    @Test
    void tokenWriteRequestsDoNotAcceptAUserIdField() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"message\":\"\"}"));
        String sessionId = session("access-token");

        ResponseEntity<String> response = http.exchange("/api/console/tokens", HttpMethod.POST,
                authedJson(sessionId, """
                        {"name":"evil","unlimited":false,"remainingQuota":0,"expiredTime":-1,"userId":999}
                        """), String.class);

        RecordedRequest upstream = NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Portal identity (session user 7) is the only identity forwarded; a client-supplied userId is ignored.
        assertThat(upstream.getBody().readUtf8()).doesNotContain("userId").doesNotContain("999");
        assertThat(upstream.getHeader("New-Api-User")).isEqualTo("7");
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        ResponseEntity<Map> response = http.exchange("/api/console/tokens", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String session(String accessToken) {
        return sessions.create(new NewApiIdentity(7L, "alice"), accessToken).getId();
    }

    private HttpEntity<String> authed(String sessionId) {
        return new HttpEntity<>(null, cookieHeaders(sessionId));
    }

    private HttpEntity<String> authedJson(String sessionId, String body) {
        HttpHeaders headers = cookieHeaders(sessionId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpHeaders cookieHeaders(String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "PORTAL_SESSION=" + sessionId);
        return headers;
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
