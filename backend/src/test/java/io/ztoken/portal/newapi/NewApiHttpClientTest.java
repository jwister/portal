package io.ztoken.portal.newapi;

import io.ztoken.portal.session.PortalPrincipal;
import io.ztoken.portal.session.NewApiIdentity;
import io.ztoken.portal.config.PortalProperties;
import io.ztoken.portal.console.DashboardSummary;
import io.ztoken.portal.console.DashboardAnalytics;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.ztoken.portal.console.TokenKey;
import io.ztoken.portal.console.TokenList;
import io.ztoken.portal.console.TokenSummary;
import io.ztoken.portal.console.TokenWriteRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

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
        assertThat(result).isEqualTo(new DashboardSummary(1000L, 100L, 8L, 20L, 0L));
        assertThat(userRequest.getPath()).isEqualTo("/api/user/self");
        assertThat(dataRequest.getPath()).startsWith("/api/data/self?")
                .contains("start_timestamp=")
                .contains("end_timestamp=");
        assertThat(dataRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-token");
        assertThat(dataRequest.getHeader("New-Api-User")).isEqualTo("7");
    }

    @Test
    void analyticsBuildsThirtyDayTokenSeriesForThirtyDayRange() throws Exception {
        Instant now = Instant.parse("2024-03-30T04:00:00Z");
        Instant olderUsage = now.minus(20, ChronoUnit.DAYS);
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("""
                        {"success":true,"data":[
                          {"created_at":%d,"model_name":"gpt-4o","quota":40,"count":2,"token_used":200},
                          {"created_at":%d,"model_name":"gpt-4o","quota":10,"count":1,"token_used":100}
                        ]}
                        """.formatted(olderUsage.getEpochSecond(), now.getEpochSecond())));

        DashboardAnalytics result = clientAt(now)
                .getDashboardAnalytics(new PortalPrincipal(7L, "alice", "access-token"), 30);

        assertThat(result.tokenUsage()).hasSize(30);
        assertThat(result.tokenUsage()).contains(
                new DashboardAnalytics.TokenUsage("2024-03-10", 200L),
                new DashboardAnalytics.TokenUsage("2024-03-30", 100L));
        assertThat(result.tokenUsage().stream()
                .mapToLong(DashboardAnalytics.TokenUsage::tokenUsage)
                .sum()).isEqualTo(300L);
        NEW_API.takeRequest();
    }

    @Test
    void analyticsAggregatesDailyUsageTopModelsAndSevenDayTokenSeries() throws Exception {
        long today = Instant.now().truncatedTo(ChronoUnit.DAYS).getEpochSecond();
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("""
                        {"success":true,"data":[
                          {"created_at":%d,"model_name":"gpt-4o","quota":40,"count":2,"token_used":10},
                          {"created_at":%d,"model_name":"gpt-4o","quota":20,"count":1,"token_used":5},
                          {"created_at":%d,"model_name":"claude","quota":30,"count":3,"token_used":8}
                        ]}
                        """.formatted(today - 86_400, today, today)));

        DashboardAnalytics result = client.getDashboardAnalytics(new PortalPrincipal(7L, "alice", "access-token"), 7);

        RecordedRequest request = NEW_API.takeRequest();
        assertThat(result.dailyUsage()).containsExactly(
                new DashboardAnalytics.DailyUsage(Instant.ofEpochSecond(today - 86_400).toString().substring(0, 10), 40L, 2L),
                new DashboardAnalytics.DailyUsage(Instant.ofEpochSecond(today).toString().substring(0, 10), 50L, 4L));
        assertThat(result.topModels()).containsExactly(
                new DashboardAnalytics.ModelUsage("gpt-4o", 60L),
                new DashboardAnalytics.ModelUsage("claude", 30L));
        assertThat(result.tokenUsage()).hasSize(7)
                .contains(new DashboardAnalytics.TokenUsage(Instant.ofEpochSecond(today - 86_400).toString().substring(0, 10), 10L))
                .contains(new DashboardAnalytics.TokenUsage(Instant.ofEpochSecond(today).toString().substring(0, 10), 13L));
        assertThat(request.getPath()).startsWith("/api/data/self?");
        assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-token");
        assertThat(request.getHeader("New-Api-User")).isEqualTo("7");
    }

    @Test
    void analyticsKeepsEmptyUpstreamStatisticsEmptyInsteadOfCreatingMockData() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":[]}"));

        DashboardAnalytics result = client.getDashboardAnalytics(new PortalPrincipal(7L, "alice", "access-token"), 30);

        assertThat(result).isEqualTo(new DashboardAnalytics(List.of(), List.of(), List.of()));
        NEW_API.takeRequest();
    }

    @Test
    void analyticsIgnoresRowsWithoutTimestampAndMapsMissingModelNameSafely() throws Exception {
        long today = Instant.now().truncatedTo(ChronoUnit.DAYS).getEpochSecond();
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("""
                        {"success":true,"data":[
                          {"model_name":"ignored","quota":99,"count":9,"token_used":9},
                          {"created_at":%d,"quota":5,"count":1,"token_used":2}
                        ]}
                        """.formatted(today)));

        DashboardAnalytics result = client.getDashboardAnalytics(new PortalPrincipal(7L, "alice", "access-token"), 7);

        assertThat(result.dailyUsage()).containsExactly(
                new DashboardAnalytics.DailyUsage(Instant.ofEpochSecond(today).toString().substring(0, 10), 5L, 1L));
        assertThat(result.topModels()).containsExactly(new DashboardAnalytics.ModelUsage("未知模型", 5L));
        assertThat(result.tokenUsage()).contains(new DashboardAnalytics.TokenUsage(
                Instant.ofEpochSecond(today).toString().substring(0, 10), 2L));
        NEW_API.takeRequest();
    }

    @Test
    void analyticsCombinesModelsAfterTheTopFiveIntoOther() throws Exception {
        long today = Instant.now().truncatedTo(ChronoUnit.DAYS).getEpochSecond();
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("""
                        {"success":true,"data":[
                          {"created_at":%d,"model_name":"model-a","quota":60,"count":0,"token_used":0},
                          {"created_at":%d,"model_name":"model-b","quota":50,"count":0,"token_used":0},
                          {"created_at":%d,"model_name":"model-c","quota":40,"count":0,"token_used":0},
                          {"created_at":%d,"model_name":"model-d","quota":30,"count":0,"token_used":0},
                          {"created_at":%d,"model_name":"model-e","quota":20,"count":0,"token_used":0},
                          {"created_at":%d,"model_name":"model-f","quota":10,"count":0,"token_used":0}
                        ]}
                        """.formatted(today, today, today, today, today, today)));

        DashboardAnalytics result = client.getDashboardAnalytics(new PortalPrincipal(7L, "alice", "access-token"), 30);

        assertThat(result.topModels()).containsExactly(
                new DashboardAnalytics.ModelUsage("model-a", 60L),
                new DashboardAnalytics.ModelUsage("model-b", 50L),
                new DashboardAnalytics.ModelUsage("model-c", 40L),
                new DashboardAnalytics.ModelUsage("model-d", 30L),
                new DashboardAnalytics.ModelUsage("model-e", 20L),
                new DashboardAnalytics.ModelUsage("__other__", 10L));
        NEW_API.takeRequest();
    }

    @Test
    void analyticsRejectsUpstreamBusinessFailureWithoutLeakingItsMessage() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":false,\"message\":\"internal statistics detail\"}"));

        Throwable thrown = catchThrowable(() -> client.getDashboardAnalytics(
                new PortalPrincipal(7L, "alice", "access-token"), 7));

        assertThat(thrown).isInstanceOf(NewApiException.class);
        assertThat(thrown.getMessage()).doesNotContain("internal statistics detail");
        NEW_API.takeRequest();
    }

    @ParameterizedTest
    @ValueSource(strings = {"quota", "count", "token_used"})
    void analyticsReturnsEmptyWhenARequiredMetricIsMissing(String missingMetric) throws Exception {
        long timestamp = Instant.parse("2024-03-09T16:30:00Z").getEpochSecond();
        String metrics = switch (missingMetric) {
            case "quota" -> "\"count\":2,\"token_used\":10";
            case "count" -> "\"quota\":40,\"token_used\":10";
            default -> "\"quota\":40,\"count\":2";
        };
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":[{\"created_at\":" + timestamp
                        + ",\"model_name\":\"gpt-4o\"," + metrics + "}]}"));

        DashboardAnalytics result = clientAt(Instant.parse("2024-03-09T16:30:00Z"))
                .getDashboardAnalytics(new PortalPrincipal(7L, "alice", "access-token"), 7);

        assertThat(result).isEqualTo(new DashboardAnalytics(List.of(), List.of(), List.of()));
        NEW_API.takeRequest();
    }

    @Test
    void analyticsReturnsEmptyWhenValidAndIncompleteRowsAreMixed() throws Exception {
        long timestamp = Instant.parse("2024-03-09T16:30:00Z").getEpochSecond();
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("""
                        {"success":true,"data":[
                          {"created_at":%d,"model_name":"gpt-4o","quota":40,"count":2,"token_used":10},
                          {"created_at":%d,"model_name":"claude","quota":30,"count":1}
                        ]}
                        """.formatted(timestamp, timestamp)));

        DashboardAnalytics result = clientAt(Instant.parse("2024-03-09T16:30:00Z"))
                .getDashboardAnalytics(new PortalPrincipal(7L, "alice", "access-token"), 7);

        assertThat(result).isEqualTo(new DashboardAnalytics(List.of(), List.of(), List.of()));
        NEW_API.takeRequest();
    }

    @Test
    void analyticsUsesShanghaiBusinessDateForDailyGroupingAndSevenDayFill() throws Exception {
        Instant earlyShanghai = Instant.parse("2024-03-09T16:30:00Z");
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("""
                        {"success":true,"data":[
                          {"created_at":%d,"model_name":"gpt-4o","quota":40,"count":2,"token_used":10}
                        ]}
                        """.formatted(earlyShanghai.getEpochSecond())));

        DashboardAnalytics result = clientAt(earlyShanghai)
                .getDashboardAnalytics(new PortalPrincipal(7L, "alice", "access-token"), 7);

        assertThat(result.dailyUsage()).containsExactly(new DashboardAnalytics.DailyUsage("2024-03-10", 40L, 2L));
        assertThat(result.tokenUsage()).containsExactly(
                new DashboardAnalytics.TokenUsage("2024-03-04", 0L),
                new DashboardAnalytics.TokenUsage("2024-03-05", 0L),
                new DashboardAnalytics.TokenUsage("2024-03-06", 0L),
                new DashboardAnalytics.TokenUsage("2024-03-07", 0L),
                new DashboardAnalytics.TokenUsage("2024-03-08", 0L),
                new DashboardAnalytics.TokenUsage("2024-03-09", 0L),
                new DashboardAnalytics.TokenUsage("2024-03-10", 10L));
        NEW_API.takeRequest();
    }

    @Test
    void analyticsQueriesWholeShanghaiCalendarDaysInsteadOfRollingHours() throws Exception {
        Instant earlyShanghai = Instant.parse("2024-03-09T16:30:00Z");
        Instant expectedStart = Instant.parse("2024-03-03T16:00:00Z");
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":[]}"));

        clientAt(earlyShanghai).getDashboardAnalytics(new PortalPrincipal(7L, "alice", "access-token"), 7);

        RecordedRequest request = NEW_API.takeRequest();
        assertThat(request.getPath()).contains("start_timestamp=" + expectedStart.getEpochSecond());
    }

    @Test
    void partiallyMissingTokenUsageIsNotReportedAsAnIncompleteTotal() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":{\"quota\":1000,\"used_quota\":100,\"request_count\":8}}"));
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":[{\"token_used\":20},{\"quota\":5}]}"));

        var result = client.getDashboard(new PortalPrincipal(7L, "alice", "access-token"));

        assertThat(result.tokenUsage()).isNull();
        NEW_API.takeRequest();
        NEW_API.takeRequest();
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
                .setHeader(HttpHeaders.SET_COOKIE, "new_api_refresh=11111111-1111-1111-1111-111111111111.refresh-1; Path=/api/user/auth; HttpOnly")
                .setBody("{\"success\":true,\"data\":{\"access_token\":\"token-1\",\"access_expires_at\":2000000000,\"session\":{\"sid\":\"11111111-1111-1111-1111-111111111111\"},\"id\":7,\"username\":\"alice\"}}"));

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
                .setHeader(HttpHeaders.SET_COOKIE, "new_api_refresh=22222222-2222-2222-2222-222222222222.refresh-2; Path=/api/user/auth; HttpOnly")
                .setBody("{\"success\":true,\"data\":{\"access_token\":\"token-2\",\"access_expires_at\":2000000000,\"session\":{\"sid\":\"22222222-2222-2222-2222-222222222222\"},\"user\":{\"id\":9,\"username\":\"bob\"}}}"));

        NewApiLogin result = client.login("bob", "secret");

        NEW_API.takeRequest();
        assertThat(result.accessToken()).isEqualTo("token-2");
        assertThat(result.identity().userId()).isEqualTo(9L);
        assertThat(result.identity().username()).isEqualTo("bob");
    }

    @Test
    void oauthCompletionParsesNewApiLoginBundleWithoutSendingBrowserCredentials() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setHeader(HttpHeaders.SET_COOKIE, "new_api_refresh=33333333-3333-3333-3333-333333333333.refresh-3; Path=/api/user/auth; HttpOnly")
                .setBody("{\"success\":true,\"data\":{\"access_token\":\"oauth-access-token\",\"access_expires_at\":2000000000,\"session\":{\"sid\":\"33333333-3333-3333-3333-333333333333\"},\"user\":{\"id\":7,\"username\":\"alice\"}}}"));

        NewApiLogin result = client.completeOAuth("github", new OAuthCallback("provider-code", "flow-token", null, null));

        RecordedRequest request = NEW_API.takeRequest();
        assertThat(result.identity()).isEqualTo(new NewApiIdentity(7L, "alice"));
        assertThat(result.accessToken()).isEqualTo("oauth-access-token");
        assertThat(result.refreshToken()).isEqualTo("33333333-3333-3333-3333-333333333333.refresh-3");
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/api/oauth/github");
        assertThat(request.getRequestUrl().queryParameter("code")).isEqualTo("provider-code");
        assertThat(request.getRequestUrl().queryParameter("state")).isEqualTo("flow-token");
        assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isNull();
        assertThat(request.getHeader("New-Api-User")).isNull();
    }

    @Test
    void oauthProviderStatusExposesOnlyPublicOAuthStartFields() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":{\"github_oauth\":true,\"github_client_id\":\"github-client\",\"oidc_enabled\":true,\"oidc_client_id\":\"google-client\",\"oidc_authorization_endpoint\":\"https://accounts.google.com/o/oauth2/v2/auth\",\"oidc_display_name\":\"Google\",\"oidc_client_secret\":\"must-not-be-projected\"}}"));

        OAuthProviderStatus result = client.getOAuthProviderStatus();

        assertThat(result).isEqualTo(new OAuthProviderStatus(true, "github-client", true, "google-client",
                "https://accounts.google.com/o/oauth2/v2/auth", "Google"));
        assertThat(NEW_API.takeRequest().getPath()).isEqualTo("/api/status");
    }

    @Test
    void registerSendsEmailAndPassword() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":null}"));

        client.register("alice", "alice@example.com", "secret", "123456");

        RecordedRequest request = NEW_API.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/user/");
        assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-access-token");
        assertThat(request.getBody().readUtf8())
                .contains("\"username\":\"alice\"")
                .contains("\"email\":\"alice@example.com\"")
                .contains("\"password\":\"secret\"")
                .contains("\"role\":1");
    }

    @Test
    void emailVerificationBusinessFailureIsClassifiedWithoutLeakingUpstreamDetails() {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":false,\"message\":\"SMTP authentication failed\"}"));

        Throwable thrown = catchThrowable(() -> client.sendEmailVerification("alice@example.com"));

        assertThat(thrown).isInstanceOf(NewApiEmailVerificationException.class);
        assertThat(thrown.getMessage()).doesNotContain("SMTP authentication failed");
        try {
            assertThat(NEW_API.takeRequest().getPath()).isEqualTo("/api/verification?email=alice@example.com");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void emailVerificationHttpFailureIsClassifiedWithoutLeakingUpstreamDetails() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setResponseCode(502)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":false,\"message\":\"SMTP gateway unavailable\"}"));

        Throwable thrown = catchThrowable(() -> client.sendEmailVerification("alice@example.com"));

        assertThat(thrown).isInstanceOf(NewApiEmailVerificationException.class);
        assertThat(thrown.getMessage()).doesNotContain("SMTP gateway unavailable");
        NEW_API.takeRequest();
    }

    @Test
    void emailVerificationRateLimitIsReportedAsRetryable() throws Exception {
        NEW_API.enqueue(new MockResponse().setResponseCode(429)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":false,\"message\":\"发送过于频繁，请等待 20 秒后再试\"}"));

        Throwable thrown = catchThrowable(() -> client.sendEmailVerification("alice@example.com"));

        assertThat(thrown).isInstanceOf(NewApiEmailVerificationException.class)
                .hasMessage("发送过于频繁，请稍后再试");
        NEW_API.takeRequest();
    }

    @Test
    void emailVerificationAlreadyRegisteredIsReportedWithoutUpstreamDetails() throws Exception {
        NEW_API.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":false,\"message\":\"该邮箱已被注册\"}"));

        Throwable thrown = catchThrowable(() -> client.sendEmailVerification("alice@example.com"));

        assertThat(thrown).isInstanceOf(NewApiEmailVerificationException.class)
                .hasMessage("该邮箱已注册，请直接登录");
        NEW_API.takeRequest();
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
    void failedLoginEmitsSanitizedDiagnosticLog() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":false,\"message\":\"账号不存在; diagnostic=expected-detail\"}"));
        Logger logger = (Logger) LoggerFactory.getLogger(NewApiHttpClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertThat(catchThrowable(() -> client.login("alice", "do-not-log-this-password")))
                    .isInstanceOf(NewApiAuthenticationException.class);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> {
                    assertThat(message).contains("NewAPI 登录被拒绝")
                            .contains("账号不存在; diagnostic=expected-detail")
                            .doesNotContain("alice")
                            .doesNotContain("do-not-log-this-password");
                });
        NEW_API.takeRequest();
    }

    @Test
    void loginHttpFailureEmitsSanitizedDiagnosticLog() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setResponseCode(502)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":false,\"message\":\"response-body-must-not-log\"}"));
        Logger logger = (Logger) LoggerFactory.getLogger(NewApiHttpClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertThat(catchThrowable(() -> client.login("alice", "do-not-log-this-password")))
                    .isInstanceOf(NewApiException.class);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> {
                    assertThat(message).contains("NewAPI 登录请求失败")
                            .contains("httpStatus=502")
                            .doesNotContain("alice")
                            .doesNotContain("do-not-log-this-password")
                            .doesNotContain("response-body-must-not-log");
                });
        NEW_API.takeRequest();
    }

    @Test
    void tokenListMapsMaskedKeyAndExtendedFields() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"success\":true,\"data\":{\"page\":2,\"page_size\":50,\"total\":101,\"items\":["
                        + "{\"id\":3,\"name\":\"server\",\"status\":1,\"remain_quota\":500,\"used_quota\":25,"
                        + "\"unlimited_quota\":false,\"expired_time\":-1,\"key\":\"sk-abcd********wxyz\"}]}}"));

        TokenList result = client.getTokens(new PortalPrincipal(7L, "alice", "access-token"), 2, 50);

        RecordedRequest request = NEW_API.takeRequest();
        assertThat(result).isEqualTo(new TokenList(2, 50, 101L, List.of(
                new TokenSummary(3L, "server", true, 500L, 25L, false, -1L, "sk-abcd********wxyz"))));
        assertThat(request.getPath()).isEqualTo("/api/token/?p=2&page_size=50");
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

    private NewApiHttpClient clientAt(Instant instant) {
        PortalProperties properties = new PortalProperties();
        properties.getNewApi().setBaseUrl(NEW_API.url("/").toString());
        return new NewApiHttpClient(properties, new ObjectMapper(), Clock.fixed(instant, ZoneId.of("Asia/Shanghai")));
    }
}
