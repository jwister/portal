package io.ztoken.portal.payment.provider;

import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/** PayPal 现有服务的渠道声明，保留既有 API 行为。 */
@Component
public class PayPalPaymentProvider implements PaymentProvider {
    private static final int ORDER_NUMBER_RANDOM_BYTES = 24;

    private final PaymentOrderRepository orders;
    private final SecureRandom random = new SecureRandom();

    public PayPalPaymentProvider(PaymentOrderRepository orders) {
        this.orders = Objects.requireNonNull(orders, "orders");
    }

    @Override public PaymentMethod method() { return PaymentMethod.PAYPAL; }

    @Override
    public PaymentOrder createOrder(long userId, long amountUsdMinor, long quotaToCredit, Instant createdAt, Instant expiresAt) {
        PaymentOrder order = PaymentOrder.paypal(nextOrderNumber(), userId, amountUsdMinor, quotaToCredit, createdAt, expiresAt);
        return orders.save(order);
    }

    private String nextOrderNumber() {
        byte[] bytes = new byte[ORDER_NUMBER_RANDOM_BYTES];
        random.nextBytes(bytes);
        return "PO_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
