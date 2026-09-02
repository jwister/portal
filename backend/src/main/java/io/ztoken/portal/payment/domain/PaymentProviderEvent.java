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
@Table(name = "payment_provider_events")
public class PaymentProviderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private PaymentMethod provider;

    @Column(name = "provider_event_id", nullable = false, updatable = false, length = 128)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 128)
    private String eventType;

    @Column(name = "payment_order_id")
    private Long paymentOrderId;

    @Column(name = "verified_at", nullable = false, updatable = false)
    private Instant verifiedAt;

    @Column(name = "audit_summary", nullable = false, updatable = false, length = 512)
    private String auditSummary;

    protected PaymentProviderEvent() {
    }

    private PaymentProviderEvent(PaymentMethod provider, String providerEventId, String eventType, Long paymentOrderId,
                                 Instant verifiedAt, String auditSummary) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.providerEventId = requireText(providerEventId, "providerEventId");
        this.eventType = requireText(eventType, "eventType");
        this.paymentOrderId = paymentOrderId;
        this.verifiedAt = Objects.requireNonNull(verifiedAt, "verifiedAt");
        this.auditSummary = requireText(auditSummary, "auditSummary");
    }

    public static PaymentProviderEvent verified(PaymentMethod provider, String providerEventId, String eventType,
                                                Long paymentOrderId, Instant verifiedAt, String auditSummary) {
        return new PaymentProviderEvent(provider, providerEventId, eventType, paymentOrderId, verifiedAt, auditSummary);
    }

    public Long getId() {
        return id;
    }

    public PaymentMethod getProvider() {
        return provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Long getPaymentOrderId() {
        return paymentOrderId;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public String getAuditSummary() {
        return auditSummary;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
