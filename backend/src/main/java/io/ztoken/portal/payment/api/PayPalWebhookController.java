package io.ztoken.portal.payment.api;

import io.ztoken.portal.payment.paypal.PayPalWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestController
public class PayPalWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PayPalWebhookController.class);

    private final PayPalWebhookService webhooks;

    public PayPalWebhookController(PayPalWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping(value = "/api/webhooks/paypal", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> receive(@RequestBody(required = false) String rawBody, HttpServletRequest request) {
        try {
            log.info("收到 PayPal Webhook 回调，开始交由验签服务处理");
            webhooks.handle(normalizedHeaders(request), rawBody == null ? "" : rawBody);
            log.info("PayPal Webhook 回调处理完成");
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException exception) {
            log.warn("PayPal Webhook 回调被拒绝：异常类型={}", exception.getClass().getSimpleName());
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException exception) {
            log.error("PayPal Webhook 回调处理暂时失败：异常类型={}", exception.getClass().getSimpleName());
            return ResponseEntity.status(503).build();
        }
    }

    private static Map<String, String> normalizedHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.putIfAbsent(name.toLowerCase(Locale.ROOT), request.getHeader(name));
        }
        return headers;
    }
}
