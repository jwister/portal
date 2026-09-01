package io.ztoken.portal.newapi;

import io.ztoken.portal.session.PortalPrincipal;
import io.ztoken.portal.session.NewApiIdentity;
import io.ztoken.portal.console.DashboardSummary;
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
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("new-api-mock-server")
class NewApiHttpClientTest {

    private static final MockWebServer NEW_API = startServer();

    @Autowired
    private NewApiClient client;

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
    void currentUserRequestUsesBearerTokenAndMatchingUserHeader() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{" + "\"success\":true,\"data\":{\"id\":7,\"username\":\"alice\"}}"));

        NewApiIdentity identity = client.getSelf(new PortalPrincipal(7L, "alice", "access-token"));

        RecordedRequest request = NEW_API.takeRequest();
        assertThat(identity).isEqualTo(new NewApiIdentity(7L, "alice"));
        assertThat(request.getPath()).isEqualTo("/api/user/self");
        assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-token");
        assertThat(request.getHeader("New-Api-User")).isEqualTo("7");
    }

    @Test
    void dashboardMapsQuotaAndOnlyRealTokenUsage() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":{\"quota\":1000,\"used_quota\":100,\"request_count\":8}}"));
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":[{\"quota\":20,\"token_used\":5},{\"token_used\":15}]}"));

        var result = client.getDashboard(new PortalPrincipal(7L, "alice", "access-token"));

        RecordedRequest userRequest = NEW_API.takeRequest();
        RecordedRequest dataRequest = NEW_API.takeRequest();
        assertThat(result).isEqualTo(new DashboardSummary(1000L, 100L, 8L, 20L));
        assertThat(userRequest.getPath()).isEqualTo("/api/user/self");
        assertThat(dataRequest.getPath()).startsWith("/api/data/self?")
                .contains("start_timestamp=")
                .contains("end_timestamp=");
        assertThat(dataRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-token");
        assertThat(dataRequest.getHeader("New-Api-User")).isEqualTo("7");
    }

    @Test
    void missingTokenFieldIsNotReportedAsZero() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":{\"quota\":1000,\"used_quota\":100,\"request_count\":8}}"));
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":[{\"quota\":20}]}"));

        var result = client.getDashboard(new PortalPrincipal(7L, "alice", "access-token"));

        assertThat(result.tokenUsage()).isNull();
        NEW_API.takeRequest();
        NEW_API.takeRequest();
    }

    @Test
    void dataBusinessFailureIsRejectedEvenWhenHttpStatusIsOk() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":{\"quota\":1000,\"used_quota\":100,\"request_count\":8}}"));
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":false,\"message\":\"internal secret detail\"}"));

        Throwable thrown = catchThrowable(() -> client.getDashboard(new PortalPrincipal(7L, "alice", "access-token")));

        assertThat(thrown).isInstanceOf(NewApiException.class);
        assertThat(thrown.getMessage()).doesNotContain("internal secret detail");
        NEW_API.takeRequest();
        NEW_API.takeRequest();
    }

    @Test
    void loginSendsCredentialsAndParsesIdentity() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":{\"access_token\":\"token-1\",\"id\":7,\"username\":\"alice\"}}"));

        NewApiLogin result = client.login("alice", "secret");

        RecordedRequest request = NEW_API.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/user/login");
        assertThat(request.getBody().readUtf8())
                .contains("\"username\":\"alice\"")
                .contains("\"password\":\"secret\"");
        assertThat(result.accessToken()).isEqualTo("token-1");
        assertThat(result.identity().userId()).isEqualTo(7L);
        assertThat(result.identity().username()).isEqualTo("alice");
    }

    @Test
    void loginParsesNestedUserObject() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":{\"access_token\":\"token-2\",\"user\":{\"id\":9,\"username\":\"bob\"}}}"));

        NewApiLogin result = client.login("bob", "secret");

        NEW_API.takeRequest();
        assertThat(result.accessToken()).isEqualTo("token-2");
        assertThat(result.identity().userId()).isEqualTo(9L);
        assertThat(result.identity().username()).isEqualTo("bob");
    }

    @Test
    void registerSendsEmailAndPassword() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":null}"));

        client.register("alice", "alice@example.com", "secret");

        RecordedRequest request = NEW_API.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/user/register");
        assertThat(request.getBody().readUtf8())
                .contains("\"username\":\"alice\"")
                .contains("\"email\":\"alice@example.com\"")
                .contains("\"password\":\"secret\"");
    }

    @Test
    void non2xxResponseBecomesNewApiExceptionWithoutBody() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":false,\"message\":\"internal secret detail\"}"));

        Throwable thrown = catchThrowable(() -> client.login("alice", "secret"));
        NEW_API.takeRequest();

        assertThat(thrown).isInstanceOf(NewApiException.class).hasMessageContaining("500");
        assertThat(thrown.getMessage()).doesNotContain("internal secret detail");
    }

    @Test
    void failedLoginThrowsAuthenticationException() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":false}"));

        assertThat(catchThrowable(() -> client.login("alice", "wrong")))
                .isInstanceOf(NewApiAuthenticationException.class);
        NEW_API.takeRequest();
    }
}
