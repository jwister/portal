package io.ztoken.portal.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/** 记录地址与精确 USDT 金额的历史占用，避免任何历史转账匹配到新订单。 */
@Entity
@Table(name = "payment_amount_registries")
public class PaymentAmountRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "payment_address_id", nullable = false)
    private PaymentAddress paymentAddress;

    @Column(name = "payable_minor", nullable = false)
    private long payableMinor;

    @OneToOne(optional = false)
    @JoinColumn(name = "payment_order_id", nullable = false)
    private PaymentOrder paymentOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentAmountRegistry() {
    }

    /** 为已创建的 TRC20 订单永久登记其地址与精确支付金额。 */
    public PaymentAmountRegistry(PaymentAddress paymentAddress, long payableMinor, PaymentOrder paymentOrder, Instant createdAt) {
        this.paymentAddress = Objects.requireNonNull(paymentAddress, "paymentAddress");
        this.payableMinor = payableMinor;
        this.paymentOrder = Objects.requireNonNull(paymentOrder, "paymentOrder");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }
}
