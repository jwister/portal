package io.ztoken.portal.payment.api;

import org.springframework.http.HttpStatus;

/** Safe, client-facing payment failures that intentionally exclude provider and upstream details. */
public final class PaymentApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String safeMessage;

    private PaymentApiException(HttpStatus status, String code, String safeMessage, Throwable cause) {
        super(safeMessage, cause, false, false);
        this.status = status;
        this.code = code;
        this.safeMessage = safeMessage;
    }

    public static PaymentApiException invalidRequest(Throwable cause) {
        return new PaymentApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_REQUEST", "Payment request is invalid", cause);
    }

    public static PaymentApiException orderNotFound() {
        return new PaymentApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "Payment order was not found", null);
    }

    public static PaymentApiException conflict(Throwable cause) {
        return new PaymentApiException(HttpStatus.CONFLICT, "PAYMENT_ACTION_CONFLICT",
                "Payment order cannot perform this action", cause);
    }

    public static PaymentApiException serviceUnavailable(Throwable cause) {
        return new PaymentApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_SERVICE_UNAVAILABLE",
                "Payment service is unavailable", cause);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getSafeMessage() {
        return safeMessage;
    }
}
