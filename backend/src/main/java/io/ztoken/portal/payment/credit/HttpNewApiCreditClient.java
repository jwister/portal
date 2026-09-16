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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@Component
public class HttpNewApiCreditClient implements NewApiCreditClient {

    private static final Logger log = LoggerFactory.getLogger(HttpNewApiCreditClient.class);
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
            log.warn("NewAPI 额度请求参数无效或支付配置不完整，结果按未知处理：用户ID={}，入账额度={}，已配置={}",
                    userId, quota, paymentProperties.getNewApiCredit().isConfigured());
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
            CreditResult finalResult = result == null ? CreditResult.UNKNOWN : result;
            log.info("NewAPI 额度 HTTP 调用完成：用户ID={}，入账额度={}，处理结果={}", userId, quota, finalResult);
            return finalResult;
        } catch (RuntimeException exception) {
            log.error("NewAPI 额度 HTTP 调用异常，结果按未知处理：用户ID={}，入账额度={}，异常类型={}",
                    userId, quota, exception.getClass().getSimpleName());
            return CreditResult.UNKNOWN;
        }
    }

    private CreditResult classify(int status, String body) {
        if (status >= 400 && status < 500) {
            log.warn("NewAPI 额度 HTTP 返回客户端错误：HTTP状态={}", status);
            return CreditResult.FAILED;
        }
        if (status < 200 || status >= 300) {
            log.warn("NewAPI 额度 HTTP 返回不可判定状态：HTTP状态={}", status);
            return CreditResult.UNKNOWN;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.has("success") || !root.path("success").isBoolean()) {
                log.warn("NewAPI 额度 HTTP 响应缺少可判定业务结果");
                return CreditResult.UNKNOWN;
            }
            return root.path("success").asBoolean() ? CreditResult.SUCCESS : CreditResult.FAILED;
        } catch (JsonProcessingException exception) {
            log.warn("NewAPI 额度 HTTP 响应无法解析为 JSON，结果按未知处理");
            return CreditResult.UNKNOWN;
        }
    }
}
