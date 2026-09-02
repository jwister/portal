package io.ztoken.portal.payment.paypal;

/** A local payment-order state conflict that the client may resolve only by observing order state. */
public class PayPalOrderConflictException extends IllegalStateException {

    public PayPalOrderConflictException(String message) {
        super(message);
    }
}
