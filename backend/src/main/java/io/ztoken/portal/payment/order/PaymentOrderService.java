package io.ztoken.portal.payment.order;

import io.ztoken.portal.payment.config.PaymentProperties;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.session.PortalPrincipal;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class PaymentOrderService {

    private static final long MIN_USD_MINOR = 100L;
    private static final long MAX_USD_MINOR = 1_000_000L;
    private static final long MINOR_UNITS_PER_USD = 100L;
    private static final int ORDER_NUMBER_RANDOM_BYTES = 24;

    private final PaymentOrderRepository orders;
    private final PaymentProperties properties;
    private final SecureRandom random = new SecureRandom();

    public PaymentOrderService(PaymentOrderRepository orders, PaymentProperties properties) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public PaymentOrderView createForUser(PortalPrincipal principal, BigDecimal amount) {
        long userId = requireUserId(principal);
        long amountUsdMinor = amountInUsdMinor(amount);
        long quotaToCredit = quotaFor(amountUsdMinor);
        Instant now = Instant.now();
        int expiryMinutes = properties.getOrderExpiryMinutes();
        if (expiryMinutes <= 0) {
            throw new IllegalStateException("Payment order expiry must be positive");
        }

        PaymentOrder order = PaymentOrder.paypal(
                nextOrderNumber(), userId, amountUsdMinor, quotaToCredit, now, now.plusSeconds(expiryMinutes * 60L));
        return PaymentOrderView.from(orders.save(order));
    }

    public Optional<PaymentOrderView> findForUser(PortalPrincipal principal, String orderNo) {
        long userId = requireUserId(principal);
        return orders.findByOrderNo(orderNo)
                .filter(order -> order.getNewApiUserId() == userId)
                .map(PaymentOrderView::from);
    }

    public List<PaymentOrderView> listForUser(PortalPrincipal principal) {
        long userId = requireUserId(principal);
        return orders.findByNewApiUserIdOrderByCreatedAtDesc(userId).stream()
                .map(PaymentOrderView::from)
                .toList();
    }

    private long amountInUsdMinor(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Payment amount is required");
        }
        try {
            long amountUsdMinor = amount.setScale(2, RoundingMode.UNNECESSARY)
                    .movePointRight(2)
                    .longValueExact();
            if (amountUsdMinor < MIN_USD_MINOR || amountUsdMinor > MAX_USD_MINOR) {
                throw new IllegalArgumentException("Payment amount must be between $1.00 and $10,000.00");
            }
            return amountUsdMinor;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Payment amount must have at most two decimal places and be in range", exception);
        }
    }

    private long quotaFor(long amountUsdMinor) {
        long quotaPerUsd = properties.getQuotaPerUsd();
        if (quotaPerUsd <= 0 || quotaPerUsd % MINOR_UNITS_PER_USD != 0) {
            throw new IllegalArgumentException("Payment quota rate must be a positive multiple of 100");
        }
        long quotaPerUsdMinor = quotaPerUsd / MINOR_UNITS_PER_USD;
        try {
            return Math.multiplyExact(amountUsdMinor, quotaPerUsdMinor);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Payment quota exceeds the supported range", exception);
        }
    }

    private long requireUserId(PortalPrincipal principal) {
        if (principal == null || principal.userId() <= 0) {
            throw new IllegalArgumentException("An authenticated Portal principal is required");
        }
        return principal.userId();
    }

    private String nextOrderNumber() {
        byte[] bytes = new byte[ORDER_NUMBER_RANDOM_BYTES];
        random.nextBytes(bytes);
        return "PO_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
