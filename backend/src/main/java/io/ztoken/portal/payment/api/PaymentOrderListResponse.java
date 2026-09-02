package io.ztoken.portal.payment.api;

import java.util.List;

public record PaymentOrderListResponse(
        List<PaymentOrderResponse> items,
        int page,
        int pageSize,
        long total
) {
}
