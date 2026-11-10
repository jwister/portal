package io.ztoken.portal.payment.api;

import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentOrderStatus;
import io.ztoken.portal.payment.order.PaymentOrderView;

import java.time.Instant;
import java.math.BigDecimal;
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
        Instant createdAt,
        BigDecimal conversionValue,
        String conversionCurrency
) {

    /** 当前订单价值以美元美分保存，转化使用美元主单位，USDT 的识别尾差不计入购买价值。 */
    static PaymentOrderResponse from(PaymentOrderView order) {
        Objects.requireNonNull(order, "order");
        return new PaymentOrderResponse(order.orderNo(), order.amountUsdMinor(), order.quotaToCredit(),
                order.method(), order.status(), order.expiresAt(), order.confirmedAt(), order.creditedAt(),
                order.createdAt(), BigDecimal.valueOf(order.amountUsdMinor(), 2), "USD");
    }

    /** 支付确认等直接返回实体的入口使用同一美元口径，避免创建、查询与确认响应不一致。 */
    static PaymentOrderResponse from(PaymentOrder order) {
        Objects.requireNonNull(order, "order");
        return new PaymentOrderResponse(order.getOrderNo(), order.getAmountUsdMinor(), order.getQuotaToCredit(),
                order.getPaymentMethod(), order.getStatus(), order.getExpiresAt(), order.getConfirmedAt(),
                order.getCreditedAt(), order.getCreatedAt(), BigDecimal.valueOf(order.getAmountUsdMinor(), 2), "USD");
    }
}
