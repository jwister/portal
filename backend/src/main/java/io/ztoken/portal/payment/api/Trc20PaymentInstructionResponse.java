package io.ztoken.portal.payment.api;

import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** 客户端仅可获得所属订单的服务端收款指引，不包含任何可篡改结算参数。 */
public record Trc20PaymentInstructionResponse(String receiveAddress, String payableAmount, String payableCurrency,
                                              PaymentOrderStatus status, Instant expiresAt, String txidCheckResult) {
    static Trc20PaymentInstructionResponse from(PaymentOrder order) {
        return new Trc20PaymentInstructionResponse(order.getReceiveAddress(),
                BigDecimal.valueOf(order.getPayableMinor(), 6).toPlainString(), order.getPayableCurrency(),
                order.getStatus(), order.getExpiresAt(), order.getLastTxidCheckResult());
    }
}
