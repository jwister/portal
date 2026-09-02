package io.ztoken.portal.payment.order;

import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentOrderStatus;

import java.time.Instant;
import java.util.Objects;

public record PaymentOrderView(
        String orderNo,
        long amountUsdMinor,
        long quotaToCredit,
        PaymentOrderStatus status,
        Instant expiresAt,
        Instant createdAt
) {

    public static PaymentOrderView from(PaymentOrder order) {
        Objects.requireNonNull(order, "order");
        return new PaymentOrderView(
                order.getOrderNo(),
                order.getAmountUsdMinor(),
                order.getQuotaToCredit(),
                order.getStatus(),
                order.getExpiresAt(),
                order.getCreatedAt()
        );
    }
}
