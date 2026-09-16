package io.ztoken.portal.payment.order;

import io.ztoken.portal.payment.config.PaymentProperties;
import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.payment.provider.PaymentProviderRegistry;
import io.ztoken.portal.session.PortalPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class PaymentOrderService {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrderService.class);

    private static final long MIN_USD_MINOR = 100L;
    private static final long MAX_USD_MINOR = 1_000_000L;
    private static final long MINOR_UNITS_PER_USD = 100L;

    private final PaymentOrderRepository orders;
    private final PaymentProperties properties;
    private final PaymentProviderRegistry providers;

    public PaymentOrderService(PaymentOrderRepository orders, PaymentProperties properties,
                               PaymentProviderRegistry providers) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.providers = Objects.requireNonNull(providers, "providers");
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
        PaymentOrder order = providers.require(method)
                .createOrder(userId, amountUsdMinor, quotaToCredit, now, expiresAt);
        log.info("支付订单创建成功：订单号={}，支付方式={}，用户ID={}，金额分={}，计划入账额度={}，过期时间={}",
                order.getOrderNo(), order.getPaymentMethod(), order.getNewApiUserId(),
                order.getAmountUsdMinor(), order.getQuotaToCredit(), order.getExpiresAt());
        return PaymentOrderView.from(order);
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

    /** 当前用户取消仍处于待支付状态的订单；订单会在锁内再次确认归属与可取消状态。 */
    @Transactional
    public PaymentOrderView cancelForUser(PortalPrincipal principal, String orderNo) {
        long userId = requireUserId(principal);
        PaymentOrder order = orders.findByOrderNoForUpdate(orderNo)
                .filter(item -> item.getNewApiUserId() == userId)
                .orElseThrow(java.util.NoSuchElementException::new);
        if (!order.cancel(Instant.now())) {
            log.warn("取消支付订单被拒绝：订单号={}，用户ID={}，当前状态={}",
                    orderNo, userId, order.getStatus());
            throw new IllegalStateException("Payment order cannot be cancelled");
        }
        PaymentOrder saved = orders.save(order);
        log.info("支付订单已由用户取消：订单号={}，用户ID={}", saved.getOrderNo(), userId);
        return PaymentOrderView.from(saved);
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

}
