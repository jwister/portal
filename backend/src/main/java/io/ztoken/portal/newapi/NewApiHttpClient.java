package io.ztoken.portal.newapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ztoken.portal.config.PortalProperties;
import io.ztoken.portal.console.DashboardSummary;
import io.ztoken.portal.console.DashboardAnalytics;
import io.ztoken.portal.console.TokenKey;
import io.ztoken.portal.console.TokenList;
import io.ztoken.portal.console.TokenSummary;
import io.ztoken.portal.console.TokenWriteRequest;
import io.ztoken.portal.console.LogEntry;
import io.ztoken.portal.console.LogPage;
import io.ztoken.portal.console.LogQuery;
import io.ztoken.portal.console.LogStats;
import io.ztoken.portal.console.Profile;
import io.ztoken.portal.console.ProfileUpdateRequest;
import io.ztoken.portal.session.NewApiIdentity;
import io.ztoken.portal.session.PortalPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Clock;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Function;
import java.util.stream.StreamSupport;

@Component
public class NewApiHttpClient implements NewApiClient, NewApiSessionRefresher {

    private static final Logger log = LoggerFactory.getLogger(NewApiHttpClient.class);
    /** 控制台按中国业务日展示统计，不能依赖服务器的默认时区。 */
    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("Asia/Shanghai");

    private final WebClient client;
    private final ObjectMapper objectMapper;
    private final String accessToken;
    /** 仅保留协议、主机和端口，用于诊断上游故障时避免泄露配置中的敏感信息。 */
    private final String upstreamTarget;
    private final Clock clock;

    @Autowired
    public NewApiHttpClient(PortalProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.system(DASHBOARD_ZONE));
    }

    /**
     * 时钟作为协作依赖注入，既让统计边界固定使用上海业务时区，也让跨日聚合可被稳定测试。
     */
    NewApiHttpClient(PortalProperties properties, ObjectMapper objectMapper, Clock clock) {
        String baseUrl = properties.getNewApi().getBaseUrl();
        this.client = WebClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.accessToken = properties.getNewApi().getAccessToken();
        this.upstreamTarget = upstreamTarget(baseUrl);
        this.clock = clock;
    }

    @Override
    public NewApiLogin login(String username, String password) {
        LoginResponse response = loginResponse(client.post(), "/api/user/login", Map.of("username", username, "password", password));
        requireSuccessfulLoginResponse(response);
        JsonNode root = response.body();
        if (root == null || !root.path("success").asBoolean(false)) {
            log.warn("NewAPI 登录被拒绝: upstream={}, httpStatus=200, success={}, message={}", upstreamTarget,
                    root != null && root.path("success").asBoolean(false), safeUpstreamMessage(root));
            throw new NewApiAuthenticationException();
        }
        JsonNode data = requireData(root);
        return loginFrom(data, response.refreshToken());
    }

    @Override
    public void logout(PortalPrincipal principal) {
        try {
            int status = client.post().uri("/api/user/auth/logout")
                    .headers(headers -> {
                        headers.setBearerAuth(principal.accessToken());
                        headers.set(HttpHeaders.COOKIE, "new_api_refresh=" + principal.refreshToken());
                        headers.set("X-Auth-Session", principal.newApiSessionId());
                    })
                    .exchangeToMono(response -> response.bodyToMono(Void.class).thenReturn(response.statusCode().value()))
                    .block(Duration.ofSeconds(10));
            if (status < 200 || status >= 300) {
                throw new NewApiException("NewAPI logout request failed with status " + status);
            }
        } catch (NewApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new NewApiException("NewAPI logout request failed");
        }
    }

    /**
     * 读取登录页启动 OAuth 所需的公开配置，避免 Portal 复制或持有第三方密钥。
     */
    @Override
    public OAuthProviderStatus getOAuthProviderStatus() {
        JsonNode data = requireData(getPublic("/api/status"));
        return new OAuthProviderStatus(
                data.path("github_oauth").asBoolean(false),
                data.path("github_client_id").asText(""),
                data.path("oidc_enabled").asBoolean(false),
                data.path("oidc_client_id").asText(""),
                data.path("oidc_authorization_endpoint").asText(""),
                data.path("oidc_display_name").asText("OIDC"));
    }

    /**
     * 由 NewAPI 生成十分钟有效的一次性 state，绑定其已有的 CSRF 防护与登录流程。
     */
    @Override
    public String createOAuthState(String provider) {
        requireSupportedOAuthProvider(provider);
        String state = requireData(post("/api/oauth/state", Map.of("provider", provider, "intent", "login"), null))
                .path("flow_token").asText();
        if (state.isBlank()) {
            throw new NewApiException("NewAPI OAuth state response did not include a flow token");
        }
        return state;
    }

    /**
     * 让 NewAPI 完成授权码交换、用户匹配与账号创建，再转换为 Portal 可保存的登录令牌。
     */
    @Override
    public NewApiLogin completeOAuth(String provider, OAuthCallback callback) {
        requireSupportedOAuthProvider(provider);
        if (callback == null || callback.state() == null || callback.state().isBlank()) {
            throw new IllegalArgumentException("OAuth state is required");
        }
        LoginResponse response = loginResponse(client.get(), oauthCallbackUri(provider, callback));
        requireSuccessfulLoginResponse(response);
        return loginFrom(requireData(response.body()), response.refreshToken());
    }

    /** refresh token 仅在服务端 Cookie 请求中使用，响应中的新 cookie 也不会转发给浏览器。 */
    @Override
    public NewApiCredentials refresh(PortalPrincipal principal) {
        try {
            LoginResponse response = client.post().uri("/api/user/auth/refresh")
                    .headers(headers -> {
                        headers.set(HttpHeaders.COOKIE, "new_api_refresh=" + principal.refreshToken());
                        headers.set("X-Auth-Session", principal.newApiSessionId());
                    })
                    .exchangeToMono(httpResponse -> httpResponse.bodyToMono(JsonNode.class)
                            .defaultIfEmpty(objectMapper.createObjectNode())
                            .map(body -> new LoginResponse(httpResponse.statusCode().value(), body,
                                    refreshCookie(httpResponse.cookies().getFirst("new_api_refresh")))))
                    .block(Duration.ofSeconds(10));
            if (response == null || response.statusCode() == 401) {
                throw new NewApiAuthenticationException();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new NewApiException("NewAPI refresh request failed with status " + response.statusCode());
            }
            JsonNode data = requireData(response.body());
            String accessToken = data.path("access_token").asText();
            String sessionId = data.path("session").path("sid").asText();
            long expiresAt = data.path("access_expires_at").asLong(0L);
            if (accessToken.isBlank() || response.refreshToken() == null || response.refreshToken().isBlank()
                    || sessionId.isBlank() || expiresAt <= Instant.now().getEpochSecond()) {
                throw new NewApiAuthenticationException();
            }
            return new NewApiCredentials(accessToken, response.refreshToken(), sessionId, Instant.ofEpochSecond(expiresAt));
        } catch (NewApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new NewApiException("NewAPI refresh request failed");
        }
    }

    @Override
    public void register(String username, String email, String password, String verificationCode) {
        Map<String, Object> body = Map.of(
                "username", username,
                "email", email,
                "password", password,
                "display_name", username,
                "role", 1
        );
        WebClient.RequestBodySpec request = client.post().uri("/api/user/");
        if (hasText(accessToken)) {
            request.headers(headers -> headers.setBearerAuth(accessToken));
        }
        request.contentType(MediaType.APPLICATION_JSON);
        try {
            JsonNode root = request.bodyValue(body).retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(10));
            requireSuccess(root);
        } catch (WebClientResponseException exception) {
            log.warn("NewAPI 用户创建失败: upstream={}, httpStatus={}", upstreamTarget, exception.getStatusCode().value());
            throw new NewApiException("NewAPI request failed with status " + exception.getStatusCode().value());
        } catch (RuntimeException exception) {
            if (exception instanceof NewApiException) {
                throw exception;
            }
            throw new NewApiException("NewAPI request failed");
        }
    }

    @Override
    public void sendEmailVerification(String email) {
        try {
            JsonNode root = client.get()
                    .uri(builder -> builder.path("/api/verification").queryParam("email", email).build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(10));
            if (root != null && root.path("success").asBoolean(false)) {
                return;
            }
            String upstreamMessage = root == null ? "" : root.path("message").asText("");
            log.warn("NewAPI email verification rejected request: {}", upstreamMessage);
            throw new NewApiEmailVerificationException(safeVerificationMessage(upstreamMessage));
        } catch (NewApiEmailVerificationException exception) {
            throw exception;
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().value() == 429) {
                throw new NewApiEmailVerificationException("发送过于频繁，请稍后再试");
            }
            log.warn("NewAPI email verification request failed with status {}", exception.getStatusCode().value());
        } catch (NewApiException exception) {
            // The registration flow must not expose SMTP or upstream transport details.
            log.warn("NewAPI email verification request failed: {}", exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("NewAPI email verification request failed", exception);
        }
        throw new NewApiEmailVerificationException();
    }

    private String safeVerificationMessage(String upstreamMessage) {
        if (upstreamMessage.contains("频繁") || upstreamMessage.toLowerCase().contains("rate")) {
            return "发送过于频繁，请稍后再试";
        }
        if (upstreamMessage.contains("已被") || upstreamMessage.contains("已注册")
                || upstreamMessage.toLowerCase().contains("already")) {
            return "该邮箱已注册，请直接登录";
        }
        if (upstreamMessage.contains("邮箱") && (upstreamMessage.contains("无效") || upstreamMessage.contains("格式"))) {
            return "邮箱地址格式不正确";
        }
        return "邮箱验证码发送服务暂不可用，请稍后重试";
    }

    @Override
    public NewApiIdentity getSelf(PortalPrincipal principal) {
        return identityFrom(getSelfData(principal));
    }

    @Override
    public DashboardSummary getDashboard(PortalPrincipal principal) {
        JsonNode user = getSelfData(principal);
        long endTimestamp = currentTimestamp();
        JsonNode data = getData("/api/data/self?start_timestamp=" + (endTimestamp - MAX_DATA_RANGE_SECONDS)
                + "&end_timestamp=" + endTimestamp, principal);
        return new DashboardSummary(
                user.path("quota").asLong(),
                user.path("used_quota").asLong(),
                user.path("request_count").asLong(),
                tokenUsageFrom(data),
                0L
        );
    }

    /**
     * 读取当前登录用户的 New API 用量明细并在服务端聚合。请求明确使用会话中的 Bearer Token 与用户编号，
     * 因此浏览器只能获取自己的图表数据，且不会接触上游访问令牌。
     */
    @Override
    public DashboardAnalytics getDashboardAnalytics(PortalPrincipal principal, int rangeDays) {
        if (rangeDays != 7 && rangeDays != 30) {
            throw new IllegalArgumentException("不支持的统计时间范围");
        }
        long endTimestamp = currentTimestamp();
        // 图表按北京时间自然日展示，起点必须是首日零点，不能使用滚动 N×24 小时窗口而混入第 N+1 天。
        LocalDate endDate = Instant.ofEpochSecond(endTimestamp).atZone(DASHBOARD_ZONE).toLocalDate();
        long startTimestamp = endDate.minusDays(rangeDays - 1L).atStartOfDay(DASHBOARD_ZONE).toEpochSecond();
        JsonNode rows = getData("/api/data/self?start_timestamp=" + startTimestamp
                + "&end_timestamp=" + endTimestamp, principal);
        Map<LocalDate, DashboardAnalytics.DailyAggregate> dailyTotals = new TreeMap<>();
        Map<String, Long> modelQuotas = new HashMap<>();

        if (!rows.isArray()) {
            throw new NewApiException("NewAPI statistics response did not include an array");
        }
        for (JsonNode row : rows) {
            if (!hasCompleteAnalyticsMetrics(row)) {
                // 三项指标任一缺失时无法保证图表口径正确，整批数据降级为空状态而不是把缺失值补成零。
                log.warn("NewAPI 用量统计存在缺少 quota、count 或 token_used 的记录，已返回空统计数据");
                return emptyAnalytics();
            }
            long createdAt = row.path("created_at").asLong(0L);
            if (createdAt <= 0L) {
                // 无时间戳的明细无法归属到某一天，直接忽略，避免将缺失字段误统计为 1970 年的数据。
                log.warn("NewAPI 用量统计存在缺少 created_at 的记录，已忽略");
                continue;
            }
            LocalDate date = Instant.ofEpochSecond(createdAt).atZone(DASHBOARD_ZONE).toLocalDate();
            long quota = row.path("quota").asLong(0L);
            long requestCount = row.path("count").asLong(0L);
            long tokenUsage = row.path("token_used").asLong(0L);
            String modelName = row.path("model_name").asText("").trim();
            if (modelName.isEmpty()) {
                // 上游偶发缺失模型名时仍保留真实额度，并以稳定的占位名称合并展示。
                modelName = "未知模型";
            }
            dailyTotals.computeIfAbsent(date, ignored -> new DashboardAnalytics.DailyAggregate())
                    .add(quota, requestCount, tokenUsage);
            modelQuotas.merge(modelName, quota, Long::sum);
        }
        return DashboardAnalytics.from(dailyTotals, modelQuotas, endDate, rangeDays);
    }

    private static final long MAX_DATA_RANGE_SECONDS = 2_592_000L;

    private long currentTimestamp() {
        return clock.instant().getEpochSecond();
    }

    /**
     * New API 的三项核心指标必须同时存在；只要缺少任一项，累计值都会误导用户，因此不返回部分结果。
     */
    private boolean hasCompleteAnalyticsMetrics(JsonNode row) {
        return row.hasNonNull("quota") && row.hasNonNull("count") && row.hasNonNull("token_used");
    }

    /** 为前端空状态提供统一、无伪造数据的统计响应。 */
    private DashboardAnalytics emptyAnalytics() {
        return new DashboardAnalytics(List.of(), List.of(), List.of());
    }

    private JsonNode getData(String uri, PortalPrincipal principal) {
        return requireData(get(uri, principal));
    }

    private Long tokenUsageFrom(JsonNode data) {
        if (!data.isArray() || data.isEmpty()) {
            return null;
        }
        long total = 0L;
        for (JsonNode item : data) {
            if (!item.hasNonNull("token_used")) {
                return null;
            }
            total += item.path("token_used").asLong();
        }
        return total;
    }

    @Override
    public TokenList getTokens(PortalPrincipal principal, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(50, pageSize));
        JsonNode data = requireData(get("/api/token/?p=" + safePage + "&page_size=" + safePageSize, principal));
        JsonNode items = data.path("items");
        if (!items.isArray()) {
            throw new NewApiException("NewAPI token response did not include items");
        }
        List<TokenSummary> tokens = StreamSupport.stream(items.spliterator(), false)
                .map(this::tokenFrom)
                .toList();
        return new TokenList(data.path("page").asInt(safePage), data.path("page_size").asInt(safePageSize),
                data.path("total").asLong(), tokens);
    }

    @Override
    public void createToken(PortalPrincipal principal, TokenWriteRequest request) {
        Map<String, Object> body = writeBody(request);
        body.put("name", request.name());
        JsonNode root = post("/api/token/", body, principal);
        requireSuccess(root);
    }

    @Override
    public TokenSummary updateToken(PortalPrincipal principal, long id, TokenWriteRequest request) {
        Map<String, Object> body = writeBody(request);
        body.put("id", id);
        body.put("name", request.name());
        JsonNode root = put("/api/token/", body, principal);
        return tokenFrom(requireData(root));
    }

    @Override
    public TokenSummary updateTokenStatus(PortalPrincipal principal, long id, boolean enabled) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("status", enabled ? 1 : 2);
        JsonNode root = put("/api/token/?status_only=true", body, principal);
        return tokenFrom(requireData(root));
    }

    @Override
    public void deleteToken(PortalPrincipal principal, long id) {
        try {
            JsonNode root = client.delete()
                    .uri("/api/token/" + id)
                    .headers(headers -> applyUserHeaders(headers, principal))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(10));
            requireSuccess(root);
        } catch (WebClientResponseException exception) {
            throw userRequestFailure(exception);
        } catch (RuntimeException exception) {
            if (exception instanceof NewApiException) {
                throw exception;
            }
            throw new NewApiException("NewAPI request failed");
        }
    }

    @Override
    public TokenKey getTokenKey(PortalPrincipal principal, long id) {
        try {
            JsonNode root = client.post()
                    .uri("/api/token/" + id + "/key")
                    .headers(headers -> applyUserHeaders(headers, principal))
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(10));
            String key = requireData(root).path("key").asText();
            if (key.isBlank()) {
                throw new NewApiException("NewAPI token key response did not include a key");
            }
            return new TokenKey(key);
        } catch (WebClientResponseException exception) {
            throw userRequestFailure(exception);
        } catch (RuntimeException exception) {
            if (exception instanceof NewApiException) {
                throw exception;
            }
            throw new NewApiException("NewAPI request failed");
        }
    }

    private Map<String, Object> writeBody(TokenWriteRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("unlimited_quota", request.unlimited());
        body.put("remain_quota", request.remainingQuota());
        body.put("expired_time", request.expiredTime());
        return body;
    }

    private TokenSummary tokenFrom(JsonNode item) {
        return new TokenSummary(
                item.path("id").asLong(),
                item.path("name").asText(),
                item.path("status").asInt() == 1,
                item.path("remain_quota").asLong(),
                item.path("used_quota").asLong(),
                item.path("unlimited_quota").asBoolean(false),
                item.path("expired_time").asLong(),
                item.path("key").asText()
        );
    }

    @Override
    public LogPage getLogs(PortalPrincipal principal, LogQuery query) {
        JsonNode page = requireData(get(logListUri(query), principal));
        JsonNode items = page.path("items");
        if (!items.isArray()) {
            throw new NewApiException("NewAPI log response did not include items");
        }
        List<LogEntry> entries = StreamSupport.stream(items.spliterator(), false)
                .map(this::logFrom).toList();
        return new LogPage(page.path("page").asInt(query.page()),
                page.path("page_size").asInt(query.pageSize()), page.path("total").asLong(), entries);
    }

    @Override
    public LogStats getLogStats(PortalPrincipal principal, LogQuery query) {
        JsonNode data = requireData(get(logStatsUri(query), principal));
        return new LogStats(data.path("quota").asLong(), data.path("rpm").asLong(), data.path("tpm").asLong());
    }

    @Override
    public Profile getProfile(PortalPrincipal principal) {
        return profileFrom(getSelfData(principal));
    }

    @Override
    public Profile updateProfile(PortalPrincipal principal, ProfileUpdateRequest request) {
        boolean updated = false;
        if (hasText(request.displayName())) {
            requireSuccess(put("/api/user/self", Map.of("display_name", request.displayName().trim()), principal));
            updated = true;
        }
        if (hasText(request.language())) {
            requireSuccess(put("/api/user/self", Map.of("language", request.language().trim()), principal));
            updated = true;
        }
        if (!updated) {
            throw new NewApiException("Profile update did not include a supported field");
        }
        return getProfile(principal);
    }

    private JsonNode get(Function<UriBuilder, URI> uriFunction, PortalPrincipal principal) {
        try {
            return client.get().uri(uriFunction).headers(headers -> applyUserHeaders(headers, principal)).retrieve()
                    .bodyToMono(JsonNode.class).block(Duration.ofSeconds(10));
        } catch (WebClientResponseException exception) {
            throw userRequestFailure(exception);
        } catch (RuntimeException exception) {
            if (exception instanceof NewApiException) throw exception;
            throw new NewApiException("NewAPI request failed");
        }
    }

    private JsonNode get(String uri, PortalPrincipal principal) {
        try {
            return client.get().uri(uri).headers(headers -> applyUserHeaders(headers, principal)).retrieve()
                    .bodyToMono(JsonNode.class).block(Duration.ofSeconds(10));
        } catch (WebClientResponseException exception) {
            throw userRequestFailure(exception);
        } catch (RuntimeException exception) {
            if (exception instanceof NewApiException) throw exception;
            throw new NewApiException("NewAPI request failed");
        }
    }

    private JsonNode getPublic(String uri) {
        try {
            return client.get().uri(uri).retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(10));
        } catch (WebClientResponseException exception) {
            throw new NewApiException("NewAPI request failed with status " + exception.getStatusCode().value());
        } catch (RuntimeException exception) {
            if (exception instanceof NewApiException) throw exception;
            throw new NewApiException("NewAPI request failed");
        }
    }

    /** 公开 OAuth 回调不带用户凭据，但仍只允许调用方提供的固定路径与参数。 */
    private JsonNode getPublic(Function<UriBuilder, URI> uriFunction) {
        try {
            return client.get().uri(uriFunction).retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(10));
        } catch (WebClientResponseException exception) {
            throw new NewApiException("NewAPI request failed with status " + exception.getStatusCode().value());
        } catch (RuntimeException exception) {
            if (exception instanceof NewApiException) throw exception;
            throw new NewApiException("NewAPI request failed");
        }
    }

    /** 将第三方的四个标准回调字段映射到固定的 NewAPI OAuth endpoint。 */
    private Function<UriBuilder, URI> oauthCallbackUri(String provider, OAuthCallback callback) {
        return uriBuilder -> {
            UriBuilder builder = uriBuilder.path("/api/oauth/" + provider)
                    .queryParam("state", callback.state());
            appendQuery(builder, "code", callback.code());
            appendQuery(builder, "error", callback.error());
            appendQuery(builder, "error_description", callback.errorDescription());
            return builder.build();
        };
    }

    private Function<UriBuilder, URI> logListUri(LogQuery query) {
        return uriBuilder -> {
            UriBuilder builder = uriBuilder.path("/api/log/self")
                    .queryParam("p", query.page())
                    .queryParam("page_size", query.pageSize());
            appendQuery(builder, "start_timestamp", query.startTimestamp());
            appendQuery(builder, "end_timestamp", query.endTimestamp());
            appendQuery(builder, "model_name", query.modelName());
            appendQuery(builder, "token_name", query.tokenName());
            appendQuery(builder, "type", query.type());
            return builder.build();
        };
    }

    private Function<UriBuilder, URI> logStatsUri(LogQuery query) {
        return uriBuilder -> {
            UriBuilder builder = uriBuilder.path("/api/log/self/stat");
            appendQuery(builder, "start_timestamp", query.startTimestamp());
            appendQuery(builder, "end_timestamp", query.endTimestamp());
            appendQuery(builder, "model_name", query.modelName());
            appendQuery(builder, "token_name", query.tokenName());
            appendQuery(builder, "type", query.type());
            return builder.build();
        };
    }

    private void appendQuery(UriBuilder builder, String name, Object value) {
        if (value instanceof String text) {
            if (hasText(text)) {
                builder.queryParam(name, text);
            }
            return;
        }
        if (value != null) {
            builder.queryParam(name, value);
        }
    }

    private LogEntry logFrom(JsonNode item) {
        JsonNode other = parseLogOther(item.path("other").asText(""));
        return new LogEntry(item.path("id").asLong(), item.path("created_at").asLong(), item.path("type").asInt(),
                item.path("content").asText(), item.path("token_name").asText(), item.path("model_name").asText(),
                item.path("quota").asLong(), item.path("prompt_tokens").asLong(), item.path("completion_tokens").asLong(),
                item.path("use_time").asLong(), item.path("is_stream").asBoolean(false), item.path("request_id").asText(),
                other.path("cache_tokens").asLong(), other.path("cache_creation_tokens").asLong(), other.path("frt").asDouble());
    }

    private JsonNode parseLogOther(String other) {
        try { return other.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(other); }
        catch (JsonProcessingException exception) { return objectMapper.createObjectNode(); }
    }

    private Profile profileFrom(JsonNode user) {
        return new Profile(user.path("id").asLong(), user.path("username").asText(), user.path("display_name").asText(),
                user.path("email").asText(), languageFrom(user.path("setting")));
    }

    private String languageFrom(JsonNode setting) {
        if (setting.isObject()) {
            return setting.path("language").isTextual() ? setting.path("language").asText() : null;
        }
        if (!setting.isTextual() || setting.asText().isBlank()) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(setting.asText());
            return parsed.path("language").isTextual() ? parsed.path("language").asText() : null;
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 登录失败日志只允许包含上游的简化说明，防止异常响应通过日志泄露凭据或过长内容。
     */
    private String safeUpstreamMessage(JsonNode root) {
        String message = root == null ? "" : root.path("message").asText("");
        String normalized = message.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.isEmpty()) {
            return "<empty>";
        }
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200) + "…";
    }

    /**
     * 配置地址可能包含路径或凭据，日志中仅记录可安全定位服务的协议、主机和端口。
     */
    private String upstreamTarget(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return "<invalid-upstream>";
            }
            return uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        } catch (IllegalArgumentException exception) {
            return "<invalid-upstream>";
        }
    }

    @Override
    public NewApiRawResponse getModelSquareStatus() {
        return proxyModelSquare("/api/status", new LinkedMultiValueMap<>(), false);
    }

    @Override
    public NewApiRawResponse getPricing() {
        return proxyModelSquare("/api/pricing", new LinkedMultiValueMap<>(), true);
    }

    @Override
    public NewApiRawResponse getPerformanceSummary(MultiValueMap<String, String> query) {
        return proxyModelSquare("/api/perf-metrics/summary", query, true);
    }

    @Override
    public NewApiRawResponse getPerformanceMetrics(MultiValueMap<String, String> query) {
        return proxyModelSquare("/api/perf-metrics", query, true);
    }

    /**
     * 仅代理模型广场明确使用的固定路径；上游响应以字节形式保留，避免 Portal 改写字段或错误状态。
     */
    private NewApiRawResponse proxyModelSquare(String path, MultiValueMap<String, String> query,
                                                 boolean includePricingToken) {
        try {
            return client.get()
                    .uri(builder -> builder.path(path).queryParams(query).build())
                    .headers(headers -> {
                        if (includePricingToken) {
                            applyPricingHeaders(headers);
                        }
                    })
                    .exchangeToMono(response -> response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0])
                            .map(body -> new NewApiRawResponse(response.statusCode(),
                                    response.headers().contentType().orElse(MediaType.APPLICATION_JSON), body)))
                    .block(Duration.ofSeconds(10));
        } catch (RuntimeException exception) {
            log.warn("NewAPI 模型广场请求失败: upstream={}, path={}, exceptionType={}", upstreamTarget, path,
                    exception.getClass().getSimpleName());
            throw new NewApiException("NewAPI model square request failed");
        }
    }

    /** 模型广场令牌只在服务器到上游的请求中加入，不能泄露给浏览器。 */
    private void applyPricingHeaders(HttpHeaders headers) {
        if (hasText(accessToken)) {
            headers.setBearerAuth(accessToken);
        }
    }

    private JsonNode getSelfData(PortalPrincipal principal) {
        return requireData(get("/api/user/self", principal));
    }

    private JsonNode post(String path, Object body, PortalPrincipal principal) {
        return send(client.post(), path, body, principal);
    }

    private JsonNode put(String path, Object body, PortalPrincipal principal) {
        return send(client.put(), path, body, principal);
    }

    private JsonNode send(WebClient.RequestBodyUriSpec request, String path, Object body, PortalPrincipal principal) {
        request.uri(path).contentType(MediaType.APPLICATION_JSON);
        if (principal != null) {
            request.headers(headers -> applyUserHeaders(headers, principal));
        }
        try {
            return request.bodyValue(body).retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(10));
        } catch (WebClientResponseException exception) {
            if (isLoginPath(path)) {
                log.warn("NewAPI 登录请求失败: upstream={}, httpStatus={}, category=http-response", upstreamTarget,
                        exception.getStatusCode().value());
            }
            if (principal != null) {
                throw userRequestFailure(exception);
            }
            throw new NewApiException("NewAPI request failed with status " + exception.getStatusCode().value());
        } catch (RuntimeException exception) {
            if (exception instanceof NewApiException) {
                throw exception;
            }
            if (isLoginPath(path)) {
                log.warn("NewAPI 登录请求异常: upstream={}, exceptionType={}", upstreamTarget,
                        exception.getClass().getSimpleName());
            }
            throw new NewApiException("NewAPI request failed");
        }
    }

    private boolean isLoginPath(String path) {
        return "/api/user/login".equals(path);
    }

    private NewApiException userRequestFailure(WebClientResponseException exception) {
        if (exception.getStatusCode().value() == 401) {
            return new NewApiAuthenticationException();
        }
        return new NewApiException("NewAPI request failed with status " + exception.getStatusCode().value());
    }

    /** 非 2xx 是上游故障而非凭据错误，日志不得携带上游响应体或登录输入。 */
    private void requireSuccessfulLoginResponse(LoginResponse response) {
        if (response != null && response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        int statusCode = response == null ? 0 : response.statusCode();
        log.warn("NewAPI 登录请求失败: upstream={}, httpStatus={}, category=http-response", upstreamTarget, statusCode);
        if (statusCode == 409 && response != null && "AUTH_SESSION_LIMIT".equals(response.body().path("code").asText())) {
            throw new NewApiSessionLimitException();
        }
        throw new NewApiException("NewAPI request failed with status " + statusCode);
    }

    /** 密码登录与 OAuth 回调都需要读取 NewAPI 的 HttpOnly refresh cookie。 */
    private LoginResponse loginResponse(WebClient.RequestBodyUriSpec request, String path, Object body) {
        request.uri(path).contentType(MediaType.APPLICATION_JSON);
        try {
            return request.bodyValue(body).exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                    .defaultIfEmpty(objectMapper.createObjectNode())
                    .map(responseBody -> new LoginResponse(response.statusCode().value(), responseBody,
                            refreshCookie(response.cookies().getFirst("new_api_refresh")))))
                    .block(Duration.ofSeconds(10));
        } catch (RuntimeException exception) {
            throw new NewApiException("NewAPI login request failed");
        }
    }

    private LoginResponse loginResponse(WebClient.RequestHeadersUriSpec<?> request, Function<UriBuilder, URI> uri) {
        try {
            return request.uri(uri).exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                    .defaultIfEmpty(objectMapper.createObjectNode())
                    .map(responseBody -> new LoginResponse(response.statusCode().value(), responseBody,
                            refreshCookie(response.cookies().getFirst("new_api_refresh")))))
                    .block(Duration.ofSeconds(10));
        } catch (RuntimeException exception) {
            throw new NewApiException("NewAPI OAuth request failed");
        }
    }

    private NewApiLogin loginFrom(JsonNode data, String refreshToken) {
        String accessToken = data.path("access_token").asText();
        String sessionId = data.path("session").path("sid").asText();
        long accessExpiresAt = data.path("access_expires_at").asLong(0L);
        JsonNode user = data.has("user") ? data.path("user") : data;
        if (accessToken.isBlank() || refreshToken == null || refreshToken.isBlank() || sessionId.isBlank()
                || accessExpiresAt <= Instant.now().getEpochSecond()) {
            throw new NewApiException("NewAPI login response did not include a complete session");
        }
        return new NewApiLogin(identityFrom(user), accessToken, refreshToken, sessionId, Instant.ofEpochSecond(accessExpiresAt));
    }

    private String refreshCookie(org.springframework.http.ResponseCookie cookie) {
        return cookie == null ? null : cookie.getValue();
    }

    private record LoginResponse(int statusCode, JsonNode body, String refreshToken) {
    }

    /** Portal 只支持已明确接入且经过部署配置的两个 OAuth provider。 */
    private void requireSupportedOAuthProvider(String provider) {
        if (!"github".equals(provider) && !"oidc".equals(provider)) {
            throw new IllegalArgumentException("Unsupported OAuth provider");
        }
    }

    private void applyUserHeaders(HttpHeaders headers, PortalPrincipal principal) {
        headers.setBearerAuth(principal.accessToken());
        headers.set("New-Api-User", String.valueOf(principal.userId()));
    }

    private JsonNode requireData(JsonNode root) {
        requireSuccess(root);
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new NewApiException("NewAPI response did not include data");
        }
        return data;
    }

    private void requireSuccess(JsonNode root) {
        if (root == null || !root.path("success").asBoolean(false)) {
            throw new NewApiException("NewAPI rejected the request");
        }
    }

    private NewApiIdentity identityFrom(JsonNode user) {
        long userId = user.path("id").asLong();
        String username = user.path("username").asText();
        if (userId <= 0 || username.isBlank()) {
            throw new NewApiException("NewAPI response did not include a valid user");
        }
        return new NewApiIdentity(userId, username);
    }
}
