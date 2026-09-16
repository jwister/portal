package io.ztoken.portal.payment.api;

import io.ztoken.portal.payment.config.PaymentProperties;
import io.ztoken.portal.payment.order.PaymentOrderService;
import io.ztoken.portal.payment.order.PaymentOrderView;
import io.ztoken.portal.payment.paypal.PayPalOrderConflictException;
import io.ztoken.portal.payment.paypal.PayPalPaymentService;
import io.ztoken.portal.session.PortalPrincipal;
import io.ztoken.portal.session.PortalSessionService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/payments/orders/{orderNo}/paypal")
public class PayPalPaymentController {

    private static final Logger log = LoggerFactory.getLogger(PayPalPaymentController.class);

    private final PaymentOrderService orders;
    private final PayPalPaymentService payments;
    private final PortalSessionService sessions;
    private final PaymentProperties properties;

    public PayPalPaymentController(PaymentOrderService orders, PayPalPaymentService payments,
                                   PortalSessionService sessions, PaymentProperties properties) {
        this.orders = orders;
        this.payments = payments;
        this.sessions = sessions;
        this.properties = properties;
    }

    @GetMapping("/config")
    public ResponseEntity<PayPalConfigResponse> config(
            @CookieValue(value = "PORTAL_SESSION", required = false) String sessionId,
            @PathVariable String orderNo) {
        requireOwnedOrder(sessionId, orderNo);
        PaymentProperties.Paypal paypal = properties.getPaypal();
        if (!paypal.isConfigured()) {
            throw PaymentApiException.serviceUnavailable(null);
        }
        String mode = "live".equalsIgnoreCase(paypal.getMode()) ? "live" : "sandbox";
        log.info("返回 PayPal 前端配置：订单号={}，运行模式={}", orderNo, mode);
        return noStore(ResponseEntity.ok(new PayPalConfigResponse(paypal.getClientId(), mode)));
    }

    @PostMapping("/order")
    public ResponseEntity<PayPalProviderOrderResponse> createProviderOrder(
            @CookieValue(value = "PORTAL_SESSION", required = false) String sessionId,
            @PathVariable String orderNo) {
        requireOwnedOrder(sessionId, orderNo);
        try {
            log.info("收到创建 PayPal 第三方订单请求：订单号={}", orderNo);
            return noStore(ResponseEntity.ok(new PayPalProviderOrderResponse(payments.createProviderOrder(orderNo))));
        } catch (PayPalOrderConflictException exception) {
            log.warn("创建 PayPal 第三方订单发生冲突：订单号={}，异常类型={}", orderNo, exception.getClass().getSimpleName());
            throw PaymentApiException.conflict(exception);
        } catch (RuntimeException exception) {
            log.error("创建 PayPal 第三方订单失败：订单号={}，异常类型={}", orderNo, exception.getClass().getSimpleName());
            throw PaymentApiException.serviceUnavailable(exception);
        }
    }

    @PostMapping("/capture")
    public ResponseEntity<PaymentOrderResponse> capture(
            @CookieValue(value = "PORTAL_SESSION", required = false) String sessionId,
            @PathVariable String orderNo) {
        requireOwnedOrder(sessionId, orderNo);
        try {
            log.info("收到 PayPal 捕获请求：订单号={}", orderNo);
            return noStore(ResponseEntity.ok(PaymentOrderResponse.from(payments.capture(orderNo))));
        } catch (PayPalOrderConflictException exception) {
            log.warn("PayPal 捕获请求发生冲突：订单号={}，异常类型={}", orderNo, exception.getClass().getSimpleName());
            throw PaymentApiException.conflict(exception);
        } catch (RuntimeException exception) {
            log.error("PayPal 捕获请求处理失败：订单号={}，异常类型={}", orderNo, exception.getClass().getSimpleName());
            throw PaymentApiException.serviceUnavailable(exception);
        }
    }

    private PaymentOrderView requireOwnedOrder(String sessionId, String orderNo) {
        PortalPrincipal principal = sessions.require(sessionId);
        return orders.findForUser(principal, orderNo).orElseThrow(PaymentApiException::orderNotFound);
    }

    private static <T> ResponseEntity<T> noStore(ResponseEntity<T> response) {
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                .cacheControl(CacheControl.noStore()).body(response.getBody());
    }
}
