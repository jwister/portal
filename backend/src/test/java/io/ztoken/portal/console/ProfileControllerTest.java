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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("profile-controller-mock-server")
class ProfileControllerTest {

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
    void profileMapsOnlySafeFieldsAndParsesLanguageFromSettingJson() throws Exception {
        NEW_API.enqueue(selfResponse("Alice", "{\\\"language\\\":\\\"zh-CN\\\"}"));
        String sessionId = session("access-token");

        ResponseEntity<Profile> response = http.exchange("/api/console/profile", HttpMethod.GET,
                authed(sessionId), Profile.class);

        RecordedRequest upstream = NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new Profile(7L, "alice", "Alice", "alice@example.com", "zh-CN"));
        assertThat(upstream.getPath()).isEqualTo("/api/user/self");
        assertThat(upstream.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-token");
        assertThat(upstream.getHeader("New-Api-User")).isEqualTo("7");
    }

    @Test
    void profileUpdateForwardsOnlyWhitelistedFields() throws Exception {
        NEW_API.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"message\":\"\"}"));
        NEW_API.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"message\":\"\"}"));
        NEW_API.enqueue(selfResponse("Alice Updated", "{\\\"language\\\":\\\"en\\\"}"));
        String sessionId = session("access-token");

        ResponseEntity<Profile> response = http.exchange("/api/console/profile", HttpMethod.PUT,
                authedJson(sessionId, """
                        {"displayName":"Alice Updated","language":"en","quota":999999,"role":100,"permission":"admin"}
                        """), Profile.class);

        RecordedRequest displayNameUpdate = NEW_API.takeRequest();
        RecordedRequest languageUpdate = NEW_API.takeRequest();
        RecordedRequest reload = NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new Profile(7L, "alice", "Alice Updated", "alice@example.com", "en"));
        assertThat(displayNameUpdate.getMethod()).isEqualTo("PUT");
        assertThat(displayNameUpdate.getPath()).isEqualTo("/api/user/self");
        assertThat(displayNameUpdate.getBody().readUtf8())
                .contains("\"display_name\":\"Alice Updated\"")
                .doesNotContain("\"language\"")
                .doesNotContain("quota")
                .doesNotContain("role")
                .doesNotContain("permission")
                .doesNotContain("sidebar_modules");
        assertThat(languageUpdate.getMethod()).isEqualTo("PUT");
        assertThat(languageUpdate.getBody().readUtf8()).contains("\"language\":\"en\"").doesNotContain("display_name");
        assertThat(reload.getPath()).isEqualTo("/api/user/self");
    }

    @Test
    void profileUpdateBusinessFailureDoesNotReturnUpstreamMessage() throws Exception {
        NEW_API.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":false,\"message\":\"upstream private detail\"}"));
        String sessionId = session("access-token");

        ResponseEntity<Map> response = http.exchange("/api/console/profile", HttpMethod.PUT,
                authedJson(sessionId, "{\"displayName\":\"Alice\"}"), Map.class);

        NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).containsEntry("code", "NEWAPI_ERROR");
        assertThat(response.getBody().get("message").toString()).doesNotContain("upstream private detail");
    }

    private MockResponse selfResponse(String displayName, String setting) {
        return new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("""
                        {"success":true,"data":{"id":7,"username":"alice","display_name":"%s",
                        "email":"alice@example.com","setting":"%s","quota":1000000,"role":1,
                        "permissions":{"admin_permissions":{"all":true}},"sidebar_modules":"{\\"admin\\":true}"}}
                        """.formatted(displayName, setting));
    }

    private String session(String accessToken) {
        return sessions.create(new NewApiIdentity(7L, "alice"), accessToken).getId();
    }

    private HttpEntity<Void> authed(String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "PORTAL_SESSION=" + sessionId);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<String> authedJson(String sessionId, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "PORTAL_SESSION=" + sessionId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
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
