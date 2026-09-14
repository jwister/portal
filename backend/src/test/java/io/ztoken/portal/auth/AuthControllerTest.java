package io.ztoken.portal.auth;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("auth-controller-new-api-mock-server")
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
                .setHeader(HttpHeaders.SET_COOKIE, "new_api_refresh=44444444-4444-4444-4444-444444444444.refresh; Path=/api/user/auth; HttpOnly")
                .setBody("""
                        {"success":true,"data":{"access_token":"newapi-access-token","access_expires_at":2000000000,
                        "session":{"sid":"44444444-4444-4444-4444-444444444444"},"user":{"id":7,"username":"alice"}}}
                        """));

        ResponseEntity<Void> response = http.postForEntity("/api/auth/login", new LoginRequest("alice", "password"), Void.class);

        RecordedRequest request = NEW_API.takeRequest();
        String requestBody = request.getBody().readUtf8();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("PORTAL_SESSION=");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).doesNotContain("Secure");
        assertThat(request.getPath()).isEqualTo("/api/user/login");
        assertThat(requestBody).contains("\"username\":\"alice\"");
        assertThat(requestBody).doesNotContain("newapi-access-token");
    }

    @Test
    void rejectedNewApiLoginReturnsUnauthorizedWithoutUpstreamMessage() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":false,\"message\":\"Username or password is incorrect\"}"));

        ResponseEntity<String> response = http.postForEntity("/api/auth/login", new LoginRequest("alice", "incorrect"), String.class);

        NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("NEWAPI_AUTH_FAILED");
        assertThat(response.getBody()).doesNotContain("Username or password is incorrect");
    }

    @Test
    void oauthProvidersExposeOnlyPublicNewApiStartConfiguration() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":{\"github_oauth\":true,\"github_client_id\":\"github-client\",\"oidc_enabled\":true,\"oidc_client_id\":\"google-client\",\"oidc_authorization_endpoint\":\"https://accounts.google.com/o/oauth2/v2/auth\",\"oidc_display_name\":\"Google\",\"oidc_client_secret\":\"do-not-expose\"}}"));

        ResponseEntity<String> response = http.getForEntity("/api/auth/oauth/providers", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("github-client").contains("google-client")
                .doesNotContain("do-not-expose");
        assertThat(NEW_API.takeRequest().getPath()).isEqualTo("/api/status");
    }

    @Test
    void oauthStateBindsNewApiFlowTokenToTheInitiatingBrowser() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":{\"flow_token\":\"flow-token\"}}"));

        ResponseEntity<String> response = http.postForEntity("/api/auth/oauth/github/state", null, String.class);
        RecordedRequest request = NEW_API.takeRequest();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("flow-token");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("PORTAL_OAUTH_STATE=flow-token", "HttpOnly", "Path=/api/auth/oauth", "SameSite=Lax");
        assertThat(request.getPath()).isEqualTo("/api/oauth/state");
        assertThat(request.getBody().readUtf8()).contains("\"provider\":\"github\"", "\"intent\":\"login\"");
    }

    @Test
    void oauthCompletionCreatesPortalSessionWithoutReturningNewApiToken() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setHeader(HttpHeaders.SET_COOKIE, "new_api_refresh=55555555-5555-5555-5555-555555555555.refresh; Path=/api/user/auth; HttpOnly")
                .setBody("{\"success\":true,\"data\":{\"access_token\":\"upstream-secret\",\"access_expires_at\":2000000000,\"session\":{\"sid\":\"55555555-5555-5555-5555-555555555555\"},\"user\":{\"id\":7,\"username\":\"alice\"}}}"));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "PORTAL_OAUTH_STATE=flow-token");
        ResponseEntity<String> response = http.exchange("/api/auth/oauth/github/complete", org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(Map.of("code", "provider-code", "state", "flow-token"), headers), String.class);
        RecordedRequest request = NEW_API.takeRequest();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).anyMatch(cookie -> cookie.contains("PORTAL_SESSION="));
        assertThat(response.getBody()).isNull();
        assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/api/oauth/github");
        assertThat(request.getRequestUrl().queryParameter("code")).isEqualTo("provider-code");
        assertThat(request.getRequestUrl().queryParameter("state")).isEqualTo("flow-token");
    }

    @Test
    void oauthCompletionRejectsStateFromAnotherBrowserBeforeCallingNewApi() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "PORTAL_OAUTH_STATE=another-browser-state");

        ResponseEntity<String> response = http.exchange("/api/auth/oauth/github/complete", org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(Map.of("code", "provider-code", "state", "flow-token"), headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("INVALID_AUTH_REQUEST");
        assertThat(NEW_API.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void unsupportedOAuthProviderIsRejectedBeforeCallingNewApi() throws Exception {
        ResponseEntity<String> response = http.postForEntity("/api/auth/oauth/discord/state", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("INVALID_AUTH_REQUEST");
        assertThat(NEW_API.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void registerForwardsEmailAccountDetailsToNewApi() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":null}"));

        ResponseEntity<Void> response = http.postForEntity("/api/auth/register",
                new RegisterRequest("alice", "alice@example.com", "password", "123456", null, null), Void.class);

        RecordedRequest request = NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(request.getPath()).isEqualTo("/api/user/register");
        assertThat(request.getBody().readUtf8()).contains("\"email\":\"alice@example.com\"")
                .contains("\"verification_code\":\"123456\"");
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

    @Test
    void statusRefreshesRejectedNewApiAccessTokenBeforeReportingTheUserAsSignedIn() throws Exception {
        String upstreamSessionId = "90a0f8ae-8d01-4a7d-979b-f775a5c24543";
        String portalSessionId = sessions.create(new io.ztoken.portal.newapi.NewApiLogin(
                new io.ztoken.portal.session.NewApiIdentity(7L, "alice"),
                "expired-access-token", upstreamSessionId + ".initial-refresh-token", upstreamSessionId,
                Instant.now().plusSeconds(60))).getId();
        NEW_API.enqueue(new MockResponse().setResponseCode(401)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":false,\"code\":\"AUTH_TOKEN_EXPIRED\"}"));
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setHeader(HttpHeaders.SET_COOKIE, "new_api_refresh=" + upstreamSessionId + ".rotated-refresh-token; Path=/api/user/auth; HttpOnly")
                .setBody("""
                        {"success":true,"data":{"access_token":"renewed-access-token","access_expires_at":2000000000,
                        "user":{"id":7,"username":"alice"},"session":{"sid":"%s"}}}
                        """.formatted(upstreamSessionId)));
        NEW_API.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":{\"id\":7,\"username\":\"alice\"}}"));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "PORTAL_SESSION=" + portalSessionId);
        ResponseEntity<AuthStatus> response = http.exchange("/api/auth/status", org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), AuthStatus.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new AuthStatus(true, new AuthProfile(7L, "alice")));
        RecordedRequest rejectedRequest = NEW_API.takeRequest();
        RecordedRequest refreshRequest = NEW_API.takeRequest();
        RecordedRequest retriedRequest = NEW_API.takeRequest();
        assertThat(rejectedRequest.getPath()).isEqualTo("/api/user/self");
        assertThat(rejectedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer expired-access-token");
        assertThat(refreshRequest.getPath()).isEqualTo("/api/user/auth/refresh");
        assertThat(refreshRequest.getHeader(HttpHeaders.COOKIE)).isEqualTo("new_api_refresh=" + upstreamSessionId + ".initial-refresh-token");
        assertThat(refreshRequest.getHeader("X-Auth-Session")).isEqualTo(upstreamSessionId);
        assertThat(retriedRequest.getPath()).isEqualTo("/api/user/self");
        assertThat(retriedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer renewed-access-token");
    }

}
