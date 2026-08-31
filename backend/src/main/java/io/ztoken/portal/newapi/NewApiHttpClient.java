package io.ztoken.portal.newapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ztoken.portal.config.PortalProperties;
import io.ztoken.portal.console.DashboardSummary;
import io.ztoken.portal.session.NewApiIdentity;
import io.ztoken.portal.session.PortalPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

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
