package io.ztoken.portal.payment.credit;

public record PaymentConfirmedEvent(String orderNo) {

    public PaymentConfirmedEvent {
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("orderNo must not be blank");
        }
    }
}
