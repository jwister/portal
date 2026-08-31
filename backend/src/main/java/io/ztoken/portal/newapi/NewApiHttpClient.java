package io.ztoken.portal.newapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ztoken.portal.config.PortalProperties;
import io.ztoken.portal.console.DashboardSummary;
import io.ztoken.portal.console.TokenList;
import io.ztoken.portal.console.TokenSummary;
import io.ztoken.portal.catalog.ModelCatalog;
import io.ztoken.portal.catalog.ModelCatalogItem;
import io.ztoken.portal.session.NewApiIdentity;
import io.ztoken.portal.session.PortalPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.List;
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
        JsonNode user = data.path("user");
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
        return new DashboardSummary(
                user.path("quota").asLong(),
                user.path("used_quota").asLong(),
                user.path("request_count").asLong()
        );
    }

    @Override
    public TokenList getTokens(PortalPrincipal principal) {
        JsonNode root = client.get()
                .uri("/api/token/?p=0&page_size=100")
                .headers(headers -> applyUserHeaders(headers, principal))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(10));
        JsonNode items = requireData(root).path("items");
        if (!items.isArray()) {
            throw new NewApiException("NewAPI token response did not include items");
        }
        List<TokenSummary> tokens = StreamSupport.stream(items.spliterator(), false)
                .map(item -> new TokenSummary(
                        item.path("id").asLong(),
                        item.path("name").asText(),
                        item.path("status").asInt() == 1,
                        item.path("remain_quota").asLong()
                ))
                .toList();
        return new TokenList(tokens);
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
            return new ModelCatalogItem(
                    model.path("model_name").asText(),
                    model.path("vendor_name").asText("Independent"),
                    model.path("enable_groups").isArray() && !model.path("enable_groups").isEmpty() ? model.path("enable_groups").get(0).asText() : "default",
                    available ? input : null,
                    available ? input * completionRatio : null,
                    available && cacheRatio != null ? input * cacheRatio : null,
                    available
            );
        }).toList();
        return new ModelCatalog(items);
    }

    private JsonNode getSelfData(PortalPrincipal principal) {
        JsonNode root = client.get()
                .uri("/api/user/self")
                .headers(headers -> applyUserHeaders(headers, principal))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(10));
        return requireData(root);
    }

    private JsonNode post(String path, Map<String, String> body, PortalPrincipal principal) {
        WebClient.RequestBodySpec request = client.post().uri(path).contentType(MediaType.APPLICATION_JSON);
        if (principal != null) {
            request.headers(headers -> applyUserHeaders(headers, principal));
        }
        return request.bodyValue(body).retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(10));
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
