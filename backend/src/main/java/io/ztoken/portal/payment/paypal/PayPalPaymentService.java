package io.ztoken.portal.payment.paypal;

import io.ztoken.portal.payment.credit.PaymentConfirmedEvent;
import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentOrderStatus;
import io.ztoken.portal.payment.domain.PaymentTransaction;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.payment.repository.PaymentTransactionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

/**
 * Creates and captures PayPal provider orders from immutable local order values.
 * The confirmation event is intentionally published while the confirmation transaction is active;
 * {@code PaymentCreditListener} consumes it only after that transaction commits.
 */
@Service
public class PayPalPaymentService {

    private static final Logger log = LoggerFactory.getLogger(PayPalPaymentService.class);

    private final PaymentOrderRepository orders;
    private final PaymentTransactionRepository transactions;
    private final PayPalClient payPal;
    private final ApplicationEventPublisher eventPublisher;

    public PayPalPaymentService(PaymentOrderRepository orders, PaymentTransactionRepository transactions,
                                PayPalClient payPal, ApplicationEventPublisher eventPublisher) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.payPal = Objects.requireNonNull(payPal, "payPal");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    @Transactional(noRollbackFor = PayPalOrderConflictException.class)
    public String createProviderOrder(String orderNo) {
        PaymentOrder order = requireWaitingPayPalOrder(orderNo);
        PaymentTransaction existing = transactions.findByPaymentOrderAndProvider(order, PaymentMethod.PAYPAL)
                .orElse(null);
        if (existing != null) {
            log.info("PayPal 订单已存在，复用第三方订单：订单号={}，PayPal订单号={}，订单状态={}",
                    order.getOrderNo(), existing.getProviderOrderId(), order.getStatus());
            return existing.getProviderOrderId();
        }

        String idempotencyKey = order.getOrderNo() + "-paypal";
        PayPalOrderDetails details = payPal.createOrder(order.getOrderNo(), order.getAmountUsdMinor(), idempotencyKey);
        validateProviderOrder(order, details);

        PaymentTransaction transaction = PaymentTransaction.paypal(order, details.orderId(), idempotencyKey, Instant.now());
        transaction.updateProviderStatus(details.status(), Instant.now());
        transactions.save(transaction);
        log.info("PayPal 第三方订单创建成功：订单号={}，PayPal订单号={}，金额分={}，第三方状态={}",
                order.getOrderNo(), transaction.getProviderOrderId(), order.getAmountUsdMinor(), details.status());
        return transaction.getProviderOrderId();
    }

    @Transactional(noRollbackFor = PayPalOrderConflictException.class)
    public PaymentOrder capture(String orderNo) {
        PaymentOrder order = requirePayPalOrder(orderNo);
        if (isAlreadyConfirmed(order.getStatus())) {
            log.info("PayPal 捕获请求幂等跳过：订单号={}，当前订单状态={}", order.getOrderNo(), order.getStatus());
            return order;
        }
        ensureWaitingAndNotExpired(order);

        PaymentTransaction transaction = transactions.findByPaymentOrderAndProvider(order, PaymentMethod.PAYPAL)
                .orElseThrow(() -> new PayPalOrderConflictException("PayPal provider order has not been created"));
        if ("COMPLETED".equals(transaction.getProviderStatus()) && hasText(transaction.getProviderCaptureId())) {
            confirm(order, transaction, transaction.getProviderCaptureId());
            return order;
        }

        PayPalCaptureDetails details = payPal.captureOrder(transaction.getProviderOrderId(), order.getOrderNo() + "-capture");
        validateCapture(order, transaction, details);
        if (!transaction.recordCapture(details.captureId(), details.status(), Instant.now())) {
            throw new IllegalStateException("PayPal capture ID does not match the existing transaction");
        }
        confirm(order, transaction, details.captureId());
        log.info("PayPal 捕获确认成功：订单号={}，PayPal订单号={}，捕获号={}，金额分={}，订单状态={}",
                order.getOrderNo(), transaction.getProviderOrderId(), details.captureId(),
                order.getAmountUsdMinor(), order.getStatus());
        return order;
    }

    private PaymentOrder requirePayPalOrder(String orderNo) {
        if (!hasText(orderNo)) {
            throw new PayPalOrderConflictException("Payment order identifier is invalid");
        }
        PaymentOrder order = orders.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new PayPalOrderConflictException("Payment order does not exist"));
        if (order.getPaymentMethod() != PaymentMethod.PAYPAL) {
            throw new PayPalOrderConflictException("Payment order is not a PayPal order");
        }
        return order;
    }

    private PaymentOrder requireWaitingPayPalOrder(String orderNo) {
        PaymentOrder order = requirePayPalOrder(orderNo);
        ensureWaitingAndNotExpired(order);
        return order;
    }

    private void ensureWaitingAndNotExpired(PaymentOrder order) {
        if (order.getStatus() != PaymentOrderStatus.WAITING_PAYMENT) {
            throw new PayPalOrderConflictException("Payment order is not waiting for payment");
        }
        Instant now = Instant.now();
        if (!order.getExpiresAt().isAfter(now)) {
            order.expireIfPast(now);
            orders.save(order);
            log.warn("PayPal 订单已过期：订单号={}，原订单状态={}，过期时间={}",
                    order.getOrderNo(), PaymentOrderStatus.WAITING_PAYMENT, order.getExpiresAt());
            throw new PayPalOrderConflictException("Payment order has expired");
        }
    }

    private void validateProviderOrder(PaymentOrder order, PayPalOrderDetails details) {
        if (details == null || !hasText(details.orderId()) || !"USD".equals(details.currency())
                || details.amountMinor() != order.getAmountUsdMinor()) {
            throw new IllegalStateException("PayPal provider order does not match the local USD amount");
        }
    }

    private void validateCapture(PaymentOrder order, PaymentTransaction transaction, PayPalCaptureDetails details) {
        if (details == null || !transaction.getProviderOrderId().equals(details.orderId())
                || !hasText(details.captureId()) || !"COMPLETED".equals(details.status())
                || !"USD".equals(details.currency()) || details.amountMinor() != order.getAmountUsdMinor()) {
            throw new IllegalStateException("PayPal capture does not match the local order");
        }
    }

    private void confirm(PaymentOrder order, PaymentTransaction transaction, String captureId) {
        if (!hasText(captureId)) {
            throw new IllegalStateException("PayPal capture ID must not be blank");
        }
        Instant now = Instant.now();
        if (!order.confirm(now)) {
            if (order.getStatus() == PaymentOrderStatus.EXPIRED) {
                orders.save(order);
                throw new PayPalOrderConflictException("Payment order has expired");
            }
            return;
        }
        transactions.save(transaction);
        orders.save(order);
        eventPublisher.publishEvent(new PaymentConfirmedEvent(order.getOrderNo()));
        log.info("PayPal 订单状态已确认：订单号={}，捕获号={}，状态=CONFIRMED", order.getOrderNo(), captureId);
    }

    private static boolean isAlreadyConfirmed(PaymentOrderStatus status) {
        return status == PaymentOrderStatus.CONFIRMED || status == PaymentOrderStatus.CREDITING
                || status == PaymentOrderStatus.PAID || status == PaymentOrderStatus.CREDIT_FAILED
                || status == PaymentOrderStatus.CREDIT_UNKNOWN;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
