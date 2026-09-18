package io.ztoken.portal.payment.order;

import io.ztoken.portal.payment.config.PaymentProperties;
import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentOrderStatus;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.payment.provider.PaymentProviderRegistry;
import io.ztoken.portal.session.PortalPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaymentOrderService {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrderService.class);

    private static final long MIN_USD_MINOR = 100L;
    private static final long MAX_USD_MINOR = 1_000_000L;
    private static final long MINOR_UNITS_PER_USD = 100L;

    private final PaymentOrderRepository orders;
    private final PaymentProperties properties;
    private final PaymentProviderRegistry providers;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Instant> lastOrderCreationTimes = new ConcurrentHashMap<>();

    @Autowired
    public PaymentOrderService(PaymentOrderRepository orders, PaymentProperties properties,
                               PaymentProviderRegistry providers) {
        this(orders, properties, providers, Clock.systemUTC());
    }

    public PaymentOrderService(PaymentOrderRepository orders, PaymentProperties properties,
                               PaymentProviderRegistry providers, Clock clock) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.providers = Objects.requireNonNull(providers, "providers");
        this.clock = Objects.requireNonNull(clock, "clock");
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

        Instant now = clock.instant();

        // 校验单用户连续创建订单的冷却时间，防止恶意高频并发刷单
        int cooldownSeconds = properties.getOrderCreationCooldownSeconds();
        if (cooldownSeconds > 0) {
            Instant lastCreated = lastOrderCreationTimes.get(userId);
            if (lastCreated != null && now.isBefore(lastCreated.plusSeconds(cooldownSeconds))) {
                log.warn("用户创建订单过于频繁，已被冷却拦截：用户ID={}，冷却时间={}秒", userId, cooldownSeconds);
                throw new IllegalArgumentException("创建订单过于频繁，请稍后再试");
            }
        }

        // 校验用户待支付订单数上限，防止恶意占用 TRC20 收款地址与动态金额池
        int maxWaitingOrders = properties.getMaxWaitingOrdersPerUser();
        if (maxWaitingOrders > 0) {
            long waitingCount = orders.countByNewApiUserIdAndStatus(userId, PaymentOrderStatus.WAITING_PAYMENT);
            if (waitingCount >= maxWaitingOrders) {
                log.warn("用户待支付订单数已达上限，拒绝创建：用户ID={}，当前待支付数={}，上限={}",
                        userId, waitingCount, maxWaitingOrders);
                throw new IllegalArgumentException("您当前已有待支付订单，请先完成或取消未支付订单后再创建新订单");
            }
        }

        long amountUsdMinor = amountInUsdMinor(amount);
        long quotaPerUsdMinor = quotaPerUsdMinor();
        if (amountUsdMinor > effectiveMaximumUsdMinor(quotaPerUsdMinor)) {
            throw new IllegalArgumentException("Payment amount exceeds the NewAPI wallet limit");
        }
        long quotaToCredit = quotaFor(amountUsdMinor, quotaPerUsdMinor);
        int expiryMinutes = properties.getOrderExpiryMinutes();
        if (expiryMinutes <= 0) {
            throw new IllegalStateException("Payment order expiry must be positive");
        }

        Instant expiresAt = now.plusSeconds(expiryMinutes * 60L);
        PaymentOrder order = providers.require(method)
                .createOrder(userId, amountUsdMinor, quotaToCredit, now, expiresAt);
        lastOrderCreationTimes.put(userId, now);
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

    /** 当前用户尝试取消订单；订单会在锁内再次确认归属，并返回持久化后的最新状态。 */
    @Transactional
    public PaymentOrderView cancelForUser(PortalPrincipal principal, String orderNo) {
        long userId = requireUserId(principal);
        PaymentOrder order = orders.findByOrderNoForUpdate(orderNo)
                .filter(item -> item.getNewApiUserId() == userId)
                .orElseThrow(java.util.NoSuchElementException::new);
        boolean cancelled = order.cancel(clock.instant());
        PaymentOrder saved = orders.save(order);
        if (!cancelled) {
            log.warn("取消支付订单被拒绝：订单号={}，用户ID={}，当前状态={}",
                    orderNo, userId, saved.getStatus());
            return PaymentOrderView.from(saved);
        }
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
