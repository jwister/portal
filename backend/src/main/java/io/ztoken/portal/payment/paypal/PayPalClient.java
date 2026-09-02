package io.ztoken.portal.payment.paypal;

import java.util.Map;

/** Boundary for the PayPal REST API. All monetary values use USD cents. */
public interface PayPalClient {

    PayPalOrderDetails createOrder(String localOrderNo, long amountUsdMinor, String idempotencyKey);

    PayPalCaptureDetails captureOrder(String providerOrderId, String idempotencyKey);

    boolean verifyWebhook(Map<String, String> headers, String rawBody);
}
