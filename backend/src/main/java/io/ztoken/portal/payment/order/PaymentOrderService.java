package io.ztoken.portal.payment.order;

import io.ztoken.portal.payment.config.PaymentProperties;
import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.session.PortalPrincipal;
import io.ztoken.portal.payment.trc20.Trc20AddressPoolService;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final Trc20AddressPoolService trc20AddressPool;
    private final SecureRandom random = new SecureRandom();

    public PaymentOrderService(PaymentOrderRepository orders, PaymentProperties properties) {
        this(orders, properties, null);
    }

    @Autowired
    public PaymentOrderService(PaymentOrderRepository orders, PaymentProperties properties,
                               Trc20AddressPoolService trc20AddressPool) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.trc20AddressPool = trc20AddressPool;
    }

    public PaymentOrderView createForUser(PortalPrincipal principal, BigDecimal amount) {
        return createForUser(principal, amount, PaymentMethod.PAYPAL);
    }

    /** 根据服务端认可的渠道创建订单；金额、额度与收款指引绝不采信浏览器输入。 */
    public PaymentOrderView createForUser(PortalPrincipal principal, BigDecimal amount, PaymentMethod method) {
        long userId = requireUserId(principal);
        if (method == null) {
            throw new IllegalArgumentException("Payment method is required");
        }
        long amountUsdMinor = amountInUsdMinor(amount);
        long quotaPerUsdMinor = quotaPerUsdMinor();
        if (amountUsdMinor > effectiveMaximumUsdMinor(quotaPerUsdMinor)) {
            throw new IllegalArgumentException("Payment amount exceeds the NewAPI wallet limit");
        }
        long quotaToCredit = quotaFor(amountUsdMinor, quotaPerUsdMinor);
        Instant now = Instant.now();
        int expiryMinutes = properties.getOrderExpiryMinutes();
        if (expiryMinutes <= 0) {
            throw new IllegalStateException("Payment order expiry must be positive");
        }

        Instant expiresAt = now.plusSeconds(expiryMinutes * 60L);
        PaymentOrder order = switch (method) {
            case PAYPAL -> PaymentOrder.paypal(nextOrderNumber(), userId, amountUsdMinor, quotaToCredit, now, expiresAt);
            case USDT_TRC20 -> createTrc20Order(userId, amountUsdMinor, quotaToCredit, now, expiresAt);
        };
        if (method == PaymentMethod.USDT_TRC20) return PaymentOrderView.from(order);
        return PaymentOrderView.from(orders.save(order));
    }

    /** TRC20 订单必须由地址池服务原子分配地址和唯一金额尾数。 */
    private PaymentOrder createTrc20Order(long userId, long amountUsdMinor, long quotaToCredit, Instant now, Instant expiresAt) {
        if (trc20AddressPool == null) {
            throw new IllegalStateException("TRC20 支付渠道未配置");
        }
        return trc20AddressPool.createOrder(userId, amountUsdMinor, quotaToCredit, now, expiresAt);
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

    private long quotaFor(long amountUsdMinor, long quotaPerUsdMinor) {
        try {
            return Math.multiplyExact(amountUsdMinor, quotaPerUsdMinor);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Payment quota exceeds the supported range", exception);
        }
    }

    private long quotaPerUsdMinor() {
        long quotaPerUsd = properties.getQuotaPerUsd();
        if (quotaPerUsd <= 0 || quotaPerUsd % MINOR_UNITS_PER_USD != 0) {
            throw new IllegalArgumentException("Payment quota rate must be a positive multiple of 100");
        }
        return quotaPerUsd / MINOR_UNITS_PER_USD;
    }

    private long effectiveMaximumUsdMinor(long quotaPerUsdMinor) {
        long walletMaximumUsdMinor = properties.getNewApiCredit().getMaxWalletQuota() / quotaPerUsdMinor;
        return Math.min(MAX_USD_MINOR, walletMaximumUsdMinor);
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
