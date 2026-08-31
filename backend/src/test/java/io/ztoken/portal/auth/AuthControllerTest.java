package io.ztoken.portal.auth;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerTest {

    private static final MockWebServer NEW_API = startServer();

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private io.ztoken.portal.session.PortalSessionService sessions;

    @AfterAll
    static void stopNewApi() throws Exception {
        NEW_API.shutdown();
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

    @DynamicPropertySource
    static void newApiProperties(DynamicPropertyRegistry registry) {
        registry.add("portal.new-api.base-url", () -> NEW_API.url("/").toString());
    }

    @Test
    void loginStoresNewApiAccessTokenInServerSession() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("""
                        {"success":true,"data":{"access_token":"newapi-access-token","user":{"id":7,"username":"alice"}}}
                        """));

        ResponseEntity<Void> response = http.postForEntity("/api/auth/login", new LoginRequest("alice", "password"), Void.class);

        RecordedRequest request = NEW_API.takeRequest();
        String requestBody = request.getBody().readUtf8();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("PORTAL_SESSION=");
        assertThat(request.getPath()).isEqualTo("/api/user/login");
        assertThat(requestBody).contains("\"username\":\"alice\"");
        assertThat(requestBody).doesNotContain("newapi-access-token");
    }

    @Test
    void registerForwardsEmailAccountDetailsToNewApi() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":null}"));

        ResponseEntity<Void> response = http.postForEntity("/api/auth/register",
                new RegisterRequest("alice", "alice@example.com", "password"), Void.class);

        RecordedRequest request = NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(request.getPath()).isEqualTo("/api/user/register");
        assertThat(request.getBody().readUtf8()).contains("\"email\":\"alice@example.com\"");
    }

    @Test
    void currentProfileComesFromThePortalSessionCookie() {
        String sessionId = sessions.create(new io.ztoken.portal.session.NewApiIdentity(7L, "alice"), "access-token").getId();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "PORTAL_SESSION=" + sessionId);

        ResponseEntity<AuthProfile> response = http.exchange("/api/auth/me", org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), AuthProfile.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new AuthProfile(7L, "alice"));
    }

    @Test
    void currentProfileRejectsMissingSessionCookie() {
        ResponseEntity<String> response = http.getForEntity("/api/auth/me", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
