package io.ztoken.portal.payment.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ztoken.portal.payment.config.PaymentProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Server-only implementation of the PayPal Orders and Webhooks REST APIs.
 * OAuth credentials and access tokens are never returned or logged.
 */
@Component
public class HttpPayPalClient implements PayPalClient {

    private static final Logger log = LoggerFactory.getLogger(HttpPayPalClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final long TOKEN_EXPIRY_SAFETY_SECONDS = 30L;

    private final PaymentProperties.Paypal properties;
    private final WebClient client;
    private final ObjectMapper objectMapper;

    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;

    @Autowired
    public HttpPayPalClient(PaymentProperties properties, ObjectMapper objectMapper) {
        this(Objects.requireNonNull(properties, "properties").getPaypal(),
                WebClient.builder().baseUrl(apiBaseUrl(properties.getPaypal())).build(), objectMapper);
    }

    HttpPayPalClient(PaymentProperties.Paypal properties, WebClient client, ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.client = Objects.requireNonNull(client, "client");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public PayPalOrderDetails createOrder(String localOrderNo, long amountUsdMinor, String idempotencyKey) {
        requireText(localOrderNo, "localOrderNo");
        requireText(idempotencyKey, "idempotencyKey");
        JsonNode response = requestJson(client.post()
                .uri("/v2/checkout/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    authorizeWithIdempotencyKey(headers, idempotencyKey);
                    headers.set("Prefer", "return=representation");
                })
                .bodyValue(Map.of(
                        "intent", "CAPTURE",
                        "purchase_units", List.of(Map.of(
                                "reference_id", localOrderNo,
                                "amount", Map.of("currency_code", "USD", "value", usd(amountUsdMinor))
                        ))
                )));
        JsonNode amount = response.path("purchase_units").path(0).path("amount");
        PayPalOrderDetails details = new PayPalOrderDetails(response.path("id").asText(), response.path("status").asText(),
                amount.path("currency_code").asText(), parseUsd(amount.path("value").asText()));
        log.info("PayPal HTTP 创建订单响应已收到：本地订单号={}，PayPal订单号={}，金额分={}，第三方状态={}",
                localOrderNo, details.orderId(), amountUsdMinor, details.status());
        return details;
    }

    @Override
    public PayPalCaptureDetails captureOrder(String providerOrderId, String idempotencyKey) {
        requireText(providerOrderId, "providerOrderId");
        requireText(idempotencyKey, "idempotencyKey");
        JsonNode response = requestJson(client.post()
                .uri("/v2/checkout/orders/{orderId}/capture", providerOrderId)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    authorizeWithIdempotencyKey(headers, idempotencyKey);
                    headers.set("Prefer", "return=representation");
                }));
        JsonNode capture = response.path("purchase_units").path(0).path("payments").path("captures").path(0);
        JsonNode amount = capture.path("amount");
        PayPalCaptureDetails details = new PayPalCaptureDetails(response.path("id").asText(), capture.path("id").asText(),
                capture.path("status").asText(), amount.path("currency_code").asText(),
                parseUsd(amount.path("value").asText()));
        log.info("PayPal HTTP 捕获订单响应已收到：PayPal订单号={}，捕获号={}，金额分={}，第三方状态={}",
                providerOrderId, details.captureId(), details.amountMinor(), details.status());
        return details;
    }

    @Override
    public boolean verifyWebhook(Map<String, String> headers, String rawBody) {
        requireConfigured();
        JsonNode webhookEvent;
        try {
            webhookEvent = objectMapper.readTree(rawBody);
        } catch (Exception ignored) {
            log.warn("PayPal Webhook 原始报文无法解析，验签失败");
            return false;
        }
        if (webhookEvent == null || !webhookEvent.isObject()) {
            log.warn("PayPal Webhook 原始报文不是 JSON 对象，验签失败");
            return false;
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("auth_algo", header(headers, "paypal-auth-algo"));
        request.put("cert_url", header(headers, "paypal-cert-url"));
        request.put("transmission_id", header(headers, "paypal-transmission-id"));
        request.put("transmission_sig", header(headers, "paypal-transmission-sig"));
        request.put("transmission_time", header(headers, "paypal-transmission-time"));
        request.put("webhook_id", properties.getWebhookId());
        request.put("webhook_event", webhookEvent);

        JsonNode response = requestJson(client.post()
                .uri("/v1/notifications/verify-webhook-signature")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders -> httpHeaders.setBearerAuth(accessToken()))
                .bodyValue(request));
        boolean verified = "SUCCESS".equals(response.path("verification_status").asText());
        log.info("PayPal Webhook 官方验签完成：验签结果={}", verified ? "成功" : "失败");
        return verified;
    }

    static String apiBaseUrl(PaymentProperties.Paypal properties) {
        Objects.requireNonNull(properties, "properties");
        return "live".equalsIgnoreCase(properties.getMode())
                ? "https://api-m.paypal.com"
                : "https://api-m.sandbox.paypal.com";
    }

    private synchronized String accessToken() {
        requireConfigured();
        Instant now = Instant.now();
        if (accessToken != null && now.isBefore(accessTokenExpiresAt)) {
            log.debug("复用已缓存的 PayPal OAuth 访问令牌");
            return accessToken;
        }

        JsonNode response = requestJson(client.post()
                .uri("/v1/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(headers -> headers.setBasicAuth(properties.getClientId(), properties.getClientSecret()))
                .body(BodyInserters.fromFormData("grant_type", "client_credentials")));
        String token = response.path("access_token").asText("").trim();
        if (token.isEmpty()) {
            throw new IllegalStateException("PayPal OAuth token response is invalid");
        }
        long expiresIn = Math.max(0L, response.path("expires_in").asLong(0L));
        accessToken = token;
        accessTokenExpiresAt = now.plusSeconds(Math.max(0L, expiresIn - TOKEN_EXPIRY_SAFETY_SECONDS));
        log.info("PayPal OAuth 访问令牌获取成功：有效期秒数={}", expiresIn);
        return token;
    }

    private JsonNode requestJson(WebClient.RequestHeadersSpec<?> request) {
        try {
            JsonNode body = request.retrieve().bodyToMono(JsonNode.class).block(REQUEST_TIMEOUT);
            return body == null ? objectMapper.createObjectNode() : body;
        } catch (RuntimeException exception) {
            log.error("PayPal HTTP 请求失败：异常类型={}", exception.getClass().getSimpleName());
            throw new IllegalStateException("PayPal service request failed", exception);
        }
    }

    private void authorizeWithIdempotencyKey(HttpHeaders headers, String idempotencyKey) {
        headers.setBearerAuth(accessToken());
        headers.set("PayPal-Request-Id", idempotencyKey);
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("PayPal payment is not configured");
        }
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return "";
        }
        return headers.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("");
    }

    private static String usd(long amountUsdMinor) {
        if (amountUsdMinor < 0) {
            throw new IllegalArgumentException("USD amount must not be negative");
        }
        return BigDecimal.valueOf(amountUsdMinor, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static long parseUsd(String value) {
        try {
            return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("PayPal returned an invalid USD amount", exception);
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
