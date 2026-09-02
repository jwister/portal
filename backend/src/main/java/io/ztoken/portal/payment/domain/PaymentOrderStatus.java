package io.ztoken.portal.payment.domain;

public enum PaymentOrderStatus {
    WAITING_PAYMENT,
    CONFIRMED,
    CREDITING,
    PAID,
    CREDIT_FAILED,
    CREDIT_UNKNOWN,
    EXPIRED,
    CANCELLED
}
