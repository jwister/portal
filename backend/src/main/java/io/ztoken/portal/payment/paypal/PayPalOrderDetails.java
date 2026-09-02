package io.ztoken.portal.payment.paypal;

/** Minimal normalized fields from a PayPal Orders create response. */
public record PayPalOrderDetails(String orderId, String status, String currency, long amountMinor) {
}
