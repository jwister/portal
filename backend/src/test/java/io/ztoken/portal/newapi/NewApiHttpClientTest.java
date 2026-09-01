package io.ztoken.portal.newapi;

import io.ztoken.portal.session.PortalPrincipal;
import io.ztoken.portal.session.NewApiIdentity;
import io.ztoken.portal.console.DashboardSummary;
import io.ztoken.portal.console.TokenKey;
import io.ztoken.portal.console.TokenList;
import io.ztoken.portal.console.TokenSummary;
import io.ztoken.portal.console.TokenWriteRequest;
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

    @Test
    void tokenListMapsMaskedKeyAndExtendedFields() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":{\"page\":1,\"page_size\":100,\"total\":1,\"items\":["
                        + "{\"id\":3,\"name\":\"server\",\"status\":1,\"remain_quota\":500,\"used_quota\":25,"
                        + "\"unlimited_quota\":false,\"expired_time\":-1,\"key\":\"sk-abcd********wxyz\"}]}}"));

        TokenList result = client.getTokens(new PortalPrincipal(7L, "alice", "access-token"));

        RecordedRequest request = NEW_API.takeRequest();
        assertThat(result.items()).containsExactly(
                new TokenSummary(3L, "server", true, 500L, 25L, false, -1L, "sk-abcd********wxyz"));
        assertThat(request.getPath()).isEqualTo("/api/token/?p=0&page_size=100");
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-token");
        assertThat(request.getHeader("New-Api-User")).isEqualTo("7");
    }

    @Test
    void createTokenForwardsSafeFieldsAndUserHeaders() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"message\":\"\"}"));

        client.createToken(new PortalPrincipal(7L, "alice", "access-token"),
                new TokenWriteRequest("app-key", true, 0L, -1L));

        RecordedRequest request = NEW_API.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/token/");
        assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-token");
        assertThat(request.getHeader("New-Api-User")).isEqualTo("7");
        assertThat(request.getBody().readUtf8())
                .contains("\"name\":\"app-key\"")
                .contains("\"unlimited_quota\":true")
                .contains("\"expired_time\":-1");
    }

    @Test
    void updateTokenSendsIdAndReturnsMaskedToken() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"message\":\"\",\"data\":{\"id\":3,\"name\":\"renamed\","
                        + "\"status\":1,\"remain_quota\":100,\"key\":\"sk-abcd********wxyz\"}}"));

        TokenSummary result = client.updateToken(new PortalPrincipal(7L, "alice", "access-token"),
                3L, new TokenWriteRequest("renamed", false, 100L, -1L));

        RecordedRequest request = NEW_API.takeRequest();
        assertThat(request.getMethod()).isEqualTo("PUT");
        assertThat(request.getPath()).isEqualTo("/api/token/");
        assertThat(request.getBody().readUtf8()).contains("\"id\":3").contains("\"name\":\"renamed\"");
        assertThat(result).isEqualTo(new TokenSummary(3L, "renamed", true, 100L, 0L, false, 0L, "sk-abcd********wxyz"));
    }

    @Test
    void updateTokenStatusUsesStatusOnlyQueryAndMapsStatusField() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"message\":\"\",\"data\":{\"id\":3,\"name\":\"server\","
                        + "\"status\":1,\"remain_quota\":500,\"key\":\"sk-abcd********wxyz\"}}"));

        TokenSummary result = client.updateTokenStatus(new PortalPrincipal(7L, "alice", "access-token"), 3L, true);

        RecordedRequest request = NEW_API.takeRequest();
        assertThat(request.getMethod()).isEqualTo("PUT");
        assertThat(request.getPath()).isEqualTo("/api/token/?status_only=true");
        assertThat(request.getBody().readUtf8()).contains("\"id\":3").contains("\"status\":1");
        assertThat(result.enabled()).isTrue();
    }

    @Test
    void disableTokenSendsDisabledStatus() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"message\":\"\",\"data\":{\"id\":3,\"name\":\"server\","
                        + "\"status\":2,\"remain_quota\":500,\"key\":\"sk-abcd********wxyz\"}}"));

        TokenSummary result = client.updateTokenStatus(new PortalPrincipal(7L, "alice", "access-token"), 3L, false);

        RecordedRequest request = NEW_API.takeRequest();
        assertThat(request.getBody().readUtf8()).contains("\"status\":2");
        assertThat(result.enabled()).isFalse();
    }

    @Test
    void deleteTokenIssuesDeleteAndChecksSuccess() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"message\":\"\"}"));

        client.deleteToken(new PortalPrincipal(7L, "alice", "access-token"), 3L);

        RecordedRequest request = NEW_API.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).isEqualTo("/api/token/3");
        assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-token");
        assertThat(request.getHeader("New-Api-User")).isEqualTo("7");
    }

    @Test
    void getTokenKeyReturnsPlaintextKeyFromKeyEndpoint() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":{\"key\":\"sk-plain-123\"}}"));

        TokenKey result = client.getTokenKey(new PortalPrincipal(7L, "alice", "access-token"), 3L);

        RecordedRequest request = NEW_API.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/token/3/key");
        assertThat(result.key()).isEqualTo("sk-plain-123");
    }

    @Test
    void tokenCreateBusinessFailureIsRejectedWhenHttpStatusIsOk() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":false,\"message\":\"已达到最大令牌数量限制\"}"));

        Throwable thrown = catchThrowable(() -> client.createToken(
                new PortalPrincipal(7L, "alice", "access-token"),
                new TokenWriteRequest("app-key", false, 100L, -1L)));
        NEW_API.takeRequest();

        assertThat(thrown).isInstanceOf(NewApiException.class);
        assertThat(thrown.getMessage()).doesNotContain("最大令牌");
    }
}
