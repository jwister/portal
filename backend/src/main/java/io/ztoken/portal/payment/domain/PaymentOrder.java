package io.ztoken.portal.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "payment_orders")
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, updatable = false, length = 48)
    private String orderNo;

    @Column(name = "newapi_user_id", nullable = false, updatable = false)
    private long newApiUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false, length = 32)
    private PaymentMethod paymentMethod;

    @Column(name = "amount_usd_minor", nullable = false, updatable = false)
    private long amountUsdMinor;

    @Column(name = "quota_to_credit", nullable = false, updatable = false)
    private long quotaToCredit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentOrderStatus status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "credited_at")
    private Instant creditedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentOrder() {
    }

    private PaymentOrder(String orderNo, long newApiUserId, PaymentMethod paymentMethod, long amountUsdMinor,
                         long quotaToCredit, Instant createdAt, Instant expiresAt) {
        this.orderNo = requireText(orderNo, "orderNo");
        this.newApiUserId = newApiUserId;
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "paymentMethod");
        this.amountUsdMinor = amountUsdMinor;
        this.quotaToCredit = quotaToCredit;
        this.status = PaymentOrderStatus.WAITING_PAYMENT;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public static PaymentOrder paypal(String orderNo, long newApiUserId, long amountUsdMinor, long quotaToCredit,
                                      Instant createdAt, Instant expiresAt) {
        return new PaymentOrder(orderNo, newApiUserId, PaymentMethod.PAYPAL, amountUsdMinor, quotaToCredit,
                createdAt, expiresAt);
    }

    public boolean confirm(Instant now) {
        Instant transitionTime = Objects.requireNonNull(now, "now");
        if (status != PaymentOrderStatus.WAITING_PAYMENT) {
            return false;
        }
        if (!expiresAt.isAfter(transitionTime)) {
            status = PaymentOrderStatus.EXPIRED;
            updatedAt = transitionTime;
            return false;
        }
        status = PaymentOrderStatus.CONFIRMED;
        confirmedAt = transitionTime;
        updatedAt = transitionTime;
        return true;
    }

    public boolean expireIfPast(Instant now) {
        Instant transitionTime = Objects.requireNonNull(now, "now");
        if (status != PaymentOrderStatus.WAITING_PAYMENT || expiresAt.isAfter(transitionTime)) {
            return false;
        }
        status = PaymentOrderStatus.EXPIRED;
        updatedAt = transitionTime;
        return true;
    }

    public boolean cancel(Instant now) {
        Instant transitionTime = Objects.requireNonNull(now, "now");
        if (status != PaymentOrderStatus.WAITING_PAYMENT) {
            return false;
        }
        if (!expiresAt.isAfter(transitionTime)) {
            status = PaymentOrderStatus.EXPIRED;
            updatedAt = transitionTime;
            return false;
        }
        status = PaymentOrderStatus.CANCELLED;
        updatedAt = transitionTime;
        return true;
    }

    public boolean startCrediting(Instant now) {
        Instant transitionTime = Objects.requireNonNull(now, "now");
        if (status != PaymentOrderStatus.CONFIRMED) {
            return false;
        }
        status = PaymentOrderStatus.CREDITING;
        updatedAt = transitionTime;
        return true;
    }

    public boolean markPaid(Instant now) {
        Instant transitionTime = Objects.requireNonNull(now, "now");
        if (status != PaymentOrderStatus.CREDITING) {
            return false;
        }
        status = PaymentOrderStatus.PAID;
        creditedAt = transitionTime;
        updatedAt = transitionTime;
        return true;
    }

    public boolean markCreditFailed(Instant now) {
        return finishCredit(PaymentOrderStatus.CREDIT_FAILED, now);
    }

    public boolean markCreditUnknown(Instant now) {
        return finishCredit(PaymentOrderStatus.CREDIT_UNKNOWN, now);
    }

    private boolean finishCredit(PaymentOrderStatus finalStatus, Instant now) {
        Instant transitionTime = Objects.requireNonNull(now, "now");
        if (status != PaymentOrderStatus.CREDITING) {
            return false;
        }
        status = finalStatus;
        updatedAt = transitionTime;
        return true;
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public long getNewApiUserId() {
        return newApiUserId;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public long getAmountUsdMinor() {
        return amountUsdMinor;
    }

    public long getQuotaToCredit() {
        return quotaToCredit;
    }

    public PaymentOrderStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getCreditedAt() {
        return creditedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
