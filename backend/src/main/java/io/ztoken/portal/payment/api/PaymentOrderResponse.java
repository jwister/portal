package io.ztoken.portal.payment.api;

import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentOrderStatus;
import io.ztoken.portal.payment.order.PaymentOrderView;

import java.time.Instant;
import java.util.Objects;

public record PaymentOrderResponse(
        String orderNo,
        long amountUsdMinor,
        long quotaToCredit,
        PaymentMethod method,
        PaymentOrderStatus status,
        Instant expiresAt,
        Instant confirmedAt,
        Instant creditedAt,
        Instant createdAt
) {

    static PaymentOrderResponse from(PaymentOrderView order) {
        Objects.requireNonNull(order, "order");
        return new PaymentOrderResponse(order.orderNo(), order.amountUsdMinor(), order.quotaToCredit(),
                PaymentMethod.PAYPAL, order.status(), order.expiresAt(), order.confirmedAt(), order.creditedAt(),
                order.createdAt());
    }

    static PaymentOrderResponse from(PaymentOrder order) {
        Objects.requireNonNull(order, "order");
        return new PaymentOrderResponse(order.getOrderNo(), order.getAmountUsdMinor(), order.getQuotaToCredit(),
                order.getPaymentMethod(), order.getStatus(), order.getExpiresAt(), order.getConfirmedAt(),
                order.getCreditedAt(), order.getCreatedAt());
    }
}
