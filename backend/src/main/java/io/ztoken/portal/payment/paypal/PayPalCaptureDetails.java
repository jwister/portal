package io.ztoken.portal.payment.paypal;

/** Minimal normalized fields from a completed PayPal capture response. */
public record PayPalCaptureDetails(String orderId, String captureId, String status, String currency,
                                   long amountMinor) {
}
