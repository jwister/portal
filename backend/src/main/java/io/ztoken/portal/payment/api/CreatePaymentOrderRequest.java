package io.ztoken.portal.payment.api;

import io.ztoken.portal.payment.domain.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentOrderRequest(
        @NotBlank String amount,
        @NotNull PaymentMethod method
) {
}
