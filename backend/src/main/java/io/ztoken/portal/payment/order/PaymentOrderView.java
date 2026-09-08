package io.ztoken.portal.payment.order;

import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentOrderStatus;
import io.ztoken.portal.payment.domain.PaymentMethod;

import java.time.Instant;
import java.util.Objects;

public record PaymentOrderView(
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

    public static PaymentOrderView from(PaymentOrder order) {
        Objects.requireNonNull(order, "order");
        return new PaymentOrderView(
                order.getOrderNo(),
                order.getAmountUsdMinor(),
                order.getQuotaToCredit(),
                order.getPaymentMethod(),
                order.getStatus(),
                order.getExpiresAt(),
                order.getConfirmedAt(),
                order.getCreditedAt(),
                order.getCreatedAt()
        );
    }
}
