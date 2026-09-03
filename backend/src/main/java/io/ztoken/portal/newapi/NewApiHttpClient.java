package io.ztoken.portal.newapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ztoken.portal.config.PortalProperties;
import io.ztoken.portal.console.DashboardSummary;
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
import io.ztoken.portal.catalog.ModelCatalog;
import io.ztoken.portal.catalog.ModelCatalogItem;
import io.ztoken.portal.session.NewApiIdentity;
import io.ztoken.portal.session.PortalPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Function;
import java.util.stream.StreamSupport;

@Component
public class NewApiHttpClient implements NewApiClient {

    private final WebClient client;
    private final ObjectMapper objectMapper;

    public NewApiHttpClient(PortalProperties properties, ObjectMapper objectMapper) {
        this.client = WebClient.builder().baseUrl(properties.getNewApi().getBaseUrl()).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public NewApiLogin login(String username, String password) {
        JsonNode root = post("/api/user/login", Map.of("username", username, "password", password), null);
        if (root == null || !root.path("success").asBoolean(false)) {
            throw new NewApiAuthenticationException();
        }
        JsonNode data = requireData(root);
        String accessToken = data.path("access_token").asText();
        JsonNode user = data.has("user") ? data.path("user") : data;
        NewApiIdentity identity = identityFrom(user);
        if (accessToken.isBlank()) {
            throw new NewApiException("NewAPI login response did not include an access token");
        }
        return new NewApiLogin(identity, accessToken);
    }

    @Override
    public void register(String username, String email, String password) {
        JsonNode root = post("/api/user/register", Map.of("username", username, "email", email, "password", password), null);
        requireSuccess(root);
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
                tokenUsageFrom(data)
        );
    }

    private static final long MAX_DATA_RANGE_SECONDS = 2_592_000L;

    private long currentTimestamp() {
        return System.currentTimeMillis() / 1_000L;
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
            throw new NewApiException("NewAPI request failed with status " + exception.getStatusCode().value());
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
            throw new NewApiException("NewAPI request failed with status " + exception.getStatusCode().value());
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
            throw new NewApiException("NewAPI request failed with status " + exception.getStatusCode().value());
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
            throw new NewApiException("NewAPI request failed with status " + exception.getStatusCode().value());
        } catch (RuntimeException exception) {
            if (exception instanceof NewApiException) throw exception;
            throw new NewApiException("NewAPI request failed");
        }
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
        return new LogEntry(item.path("id").asLong(), item.path("created_at").asLong(), item.path("type").asInt(),
                item.path("content").asText(), item.path("token_name").asText(), item.path("model_name").asText(),
                item.path("quota").asLong(), item.path("prompt_tokens").asLong(), item.path("completion_tokens").asLong(),
                item.path("use_time").asLong(), item.path("is_stream").asBoolean(false), item.path("request_id").asText());
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

    @Override
    public ModelCatalog getModelCatalog() {
        JsonNode root = client.get().uri("/api/pricing").retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(10));
        JsonNode models = requireData(root);
        if (!models.isArray()) throw new NewApiException("NewAPI pricing response did not include models");
        List<ModelCatalogItem> items = StreamSupport.stream(models.spliterator(), false).map(model -> {
            Double input = model.hasNonNull("model_price") ? model.path("model_price").asDouble() : null;
            Double completionRatio = model.hasNonNull("completion_ratio") ? model.path("completion_ratio").asDouble() : null;
            Double cacheRatio = model.hasNonNull("cache_ratio") ? model.path("cache_ratio").asDouble() : null;
            boolean available = input != null && completionRatio != null && model.path("quota_type").asInt() == 0;
            List<String> groups = model.path("enable_groups").isArray()
                    ? StreamSupport.stream(model.path("enable_groups").spliterator(), false)
                    .map(JsonNode::asText)
                    .filter(this::hasText)
                    .toList()
                    : List.of();
            if (groups.isEmpty()) {
                groups = List.of("default");
            }
            return new ModelCatalogItem(
                    model.path("model_name").asText(),
                    model.path("vendor_name").asText("Independent"),
                    groups,
                    available ? input : null,
                    available ? input * completionRatio : null,
                    available && cacheRatio != null ? input * cacheRatio : null,
                    available
            );
        }).toList();
        return new ModelCatalog(items);
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
            throw new NewApiException("NewAPI request failed with status " + exception.getStatusCode().value());
        } catch (RuntimeException exception) {
            if (exception instanceof NewApiException) {
                throw exception;
            }
            throw new NewApiException("NewAPI request failed");
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
