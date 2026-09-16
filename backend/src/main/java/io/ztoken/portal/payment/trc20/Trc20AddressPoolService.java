package io.ztoken.portal.payment.trc20;

import io.ztoken.portal.payment.domain.PaymentAddress;
import io.ztoken.portal.payment.domain.PaymentAmountRegistry;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.repository.PaymentAddressRepository;
import io.ztoken.portal.payment.repository.PaymentAmountRegistryRepository;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 负责从多地址池中分配负载最低地址及不重复的 USDT 金额识别码。 */
@Service
public class Trc20AddressPoolService {

    private static final Logger log = LoggerFactory.getLogger(Trc20AddressPoolService.class);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long MIN_SUFFIX = 1L;
    private static final long MAX_SUFFIX = 9_999L;

    private final PaymentAddressRepository addresses;
    private final PaymentAmountRegistryRepository amounts;
    private final PaymentOrderRepository orders;
    private final boolean amountSuffixEnabled;

    @Autowired
    public Trc20AddressPoolService(PaymentAddressRepository addresses, PaymentAmountRegistryRepository amounts,
                                   PaymentOrderRepository orders, io.ztoken.portal.payment.config.PaymentProperties properties) {
        this(addresses, amounts, orders, properties.getTrc20().isAmountSuffixEnabled());
    }

    /** 供单元测试传入明确尾数开关，避免依赖 Spring 配置绑定。 */
    public Trc20AddressPoolService(PaymentAddressRepository addresses, PaymentAmountRegistryRepository amounts,
                                   PaymentOrderRepository orders, boolean amountSuffixEnabled) {
        this.addresses = Objects.requireNonNull(addresses, "addresses");
        this.amounts = Objects.requireNonNull(amounts, "amounts");
        this.orders = Objects.requireNonNull(orders, "orders");
        this.amountSuffixEnabled = amountSuffixEnabled;
    }

    /** 在同一数据库事务内锁定地址、分配精确金额并永久登记金额占用。 */
    @Transactional
    public PaymentOrder createOrder(long userId, long amountUsdMinor, long quotaToCredit, Instant now, Instant expiresAt) {
        List<PaymentAddress> pool = addresses.lockEnabledOrderedByLoad();
        if (pool.isEmpty()) {
            throw new IllegalStateException("没有可用的 TRC20 收款地址");
        }
        long basePayableMinor = Math.multiplyExact(amountUsdMinor, 10_000L);
        for (PaymentAddress address : pool) {
            for (long suffix = amountSuffixEnabled ? MIN_SUFFIX : 0L; suffix <= (amountSuffixEnabled ? MAX_SUFFIX : 0L); suffix++) {
                long payableMinor = Math.addExact(basePayableMinor, suffix);
                if (amounts.existsByPaymentAddressAndPayableMinor(address, payableMinor)) continue;
                PaymentOrder order = PaymentOrder.usdtTrc20(nextOrderNo(), userId, amountUsdMinor, quotaToCredit,
                        address, payableMinor, now, expiresAt);
                orders.save(order);
                amounts.save(new PaymentAmountRegistry(address, payableMinor, order, now));
                address.incrementActiveOrderCount();
                log.info("TRC20 收款地址与识别金额分配成功：订单号={}，用户ID={}，收款地址={}，应付最小单位={}，金额分={}，计划入账额度={}，过期时间={}",
                        order.getOrderNo(), userId, address.getAddress(), payableMinor, amountUsdMinor, quotaToCredit, expiresAt);
                return order;
            }
        }
        throw new IllegalStateException("所有 TRC20 收款地址的金额识别码均已用尽");
    }

    /** 生成与既有 Portal 订单相同格式的不可预测公开订单号。 */
    private String nextOrderNo() {
        return "PO_TRON_" + Long.toUnsignedString(RANDOM.nextLong(), 36) + Long.toUnsignedString(RANDOM.nextLong(), 36);
    }
}
