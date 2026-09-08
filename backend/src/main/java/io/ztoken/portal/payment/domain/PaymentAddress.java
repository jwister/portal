package io.ztoken.portal.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/** TRC20 地址池中的收款地址及其当前待支付订单负载。 */
@Entity
@Table(name = "payment_addresses")
public class PaymentAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String address;

    @Column(name = "active_order_count", nullable = false)
    private int activeOrderCount;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentAddress() {
    }

    /** 创建可用于分配新 TRC20 订单的地址池记录。 */
    public PaymentAddress(String address, Instant createdAt) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("TRC20 收款地址不能为空");
        }
        this.address = address;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Long getId() { return id; }
    public String getAddress() { return address; }
    public int getActiveOrderCount() { return activeOrderCount; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }

    /** 分配订单时增加负载，供后续地址池按负载排序。 */
    public void incrementActiveOrderCount() { activeOrderCount++; }

    /** 订单确认或过期时释放一次地址负载。 */
    public void decrementActiveOrderCount() {
        if (activeOrderCount > 0) activeOrderCount--;
    }
}
