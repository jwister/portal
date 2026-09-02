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
@Table(name = "credit_attempts")
public class CreditAttempt {

    public enum Status {
        PROCESSING,
        SUCCESS,
        FAILED,
        UNKNOWN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_order_id", nullable = false, updatable = false)
    private PaymentOrder paymentOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status;

    @Column(length = 512)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected CreditAttempt() {
    }

    private CreditAttempt(PaymentOrder paymentOrder, Instant createdAt) {
        this.paymentOrder = Objects.requireNonNull(paymentOrder, "paymentOrder");
        this.status = Status.PROCESSING;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static CreditAttempt started(PaymentOrder paymentOrder, Instant createdAt) {
        return new CreditAttempt(paymentOrder, createdAt);
    }

    public boolean finish(Status result, String message, Instant now) {
        if (status != Status.PROCESSING) {
            return false;
        }
        if (result == Status.PROCESSING) {
            throw new IllegalArgumentException("A credit attempt cannot finish as PROCESSING");
        }
        status = Objects.requireNonNull(result, "result");
        this.message = message;
        finishedAt = Objects.requireNonNull(now, "now");
        return true;
    }

    public Long getId() {
        return id;
    }

    public PaymentOrder getPaymentOrder() {
        return paymentOrder;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
