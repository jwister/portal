package io.ztoken.portal.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_order_id", nullable = false, updatable = false)
    private PaymentOrder paymentOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private PaymentMethod provider;

    @Column(name = "provider_order_id", nullable = false, updatable = false, length = 128)
    private String providerOrderId;

    @Column(name = "provider_capture_id", length = 128)
    private String providerCaptureId;

    @Column(name = "provider_status", length = 64)
    private String providerStatus;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 96)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentTransaction() {
    }

    private PaymentTransaction(PaymentOrder paymentOrder, PaymentMethod provider, String providerOrderId,
                               String idempotencyKey, Instant createdAt) {
        this.paymentOrder = Objects.requireNonNull(paymentOrder, "paymentOrder");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.providerOrderId = requireText(providerOrderId, "providerOrderId");
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
    }

    public static PaymentTransaction paypal(PaymentOrder paymentOrder, String providerOrderId,
                                            String idempotencyKey, Instant createdAt) {
        return new PaymentTransaction(paymentOrder, PaymentMethod.PAYPAL, providerOrderId, idempotencyKey, createdAt);
    }

    public boolean recordCapture(String captureId, String status, Instant now) {
        Instant updatedAt = Objects.requireNonNull(now, "now");
        String newCaptureId = requireText(captureId, "captureId");
        if (providerCaptureId != null && !providerCaptureId.equals(newCaptureId)) {
            return false;
        }
        providerCaptureId = newCaptureId;
        providerStatus = status;
        this.updatedAt = updatedAt;
        return true;
    }

    public void updateProviderStatus(String status, Instant now) {
        providerStatus = status;
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public Long getId() {
        return id;
    }

    public PaymentOrder getPaymentOrder() {
        return paymentOrder;
    }

    public PaymentMethod getProvider() {
        return provider;
    }

    public String getProviderOrderId() {
        return providerOrderId;
    }

    public String getProviderCaptureId() {
        return providerCaptureId;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
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
