package io.ztoken.portal.payment.credit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ztoken.portal.config.PortalProperties;
import io.ztoken.portal.payment.config.PaymentProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@Component
public class HttpNewApiCreditClient implements NewApiCreditClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient client;
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;

    @Autowired
    public HttpNewApiCreditClient(PortalProperties portalProperties, PaymentProperties paymentProperties,
                                  ObjectMapper objectMapper) {
        this(portalProperties, paymentProperties, objectMapper, REQUEST_TIMEOUT);
    }

    HttpNewApiCreditClient(PortalProperties portalProperties, PaymentProperties paymentProperties,
                           ObjectMapper objectMapper, Duration requestTimeout) {
        Objects.requireNonNull(portalProperties, "portalProperties");
        this.paymentProperties = Objects.requireNonNull(paymentProperties, "paymentProperties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.client = WebClient.builder()
                .baseUrl(portalProperties.getNewApi().getBaseUrl())
                .build();
    }

    @Override
    public CreditResult addQuota(long userId, long quota) {
        if (userId <= 0 || quota <= 0 || !paymentProperties.getNewApiCredit().isConfigured()) {
            return CreditResult.UNKNOWN;
        }

        try {
            CreditResult result = client.post()
                    .uri("/api/user/manage")
                    .headers(headers -> headers.setBearerAuth(paymentProperties.getNewApiCredit().getAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("id", userId, "action", "add_quota", "mode", "add", "value", quota))
                    .exchangeToMono(response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> classify(response.statusCode().value(), body)))
                    .block(requestTimeout);
            return result == null ? CreditResult.UNKNOWN : result;
        } catch (RuntimeException exception) {
            return CreditResult.UNKNOWN;
        }
    }

    private CreditResult classify(int status, String body) {
        if (status >= 400 && status < 500) {
            return CreditResult.FAILED;
        }
        if (status < 200 || status >= 300) {
            return CreditResult.UNKNOWN;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.has("success") || !root.path("success").isBoolean()) {
                return CreditResult.UNKNOWN;
            }
            return root.path("success").asBoolean() ? CreditResult.SUCCESS : CreditResult.FAILED;
        } catch (JsonProcessingException exception) {
            return CreditResult.UNKNOWN;
        }
    }
}
