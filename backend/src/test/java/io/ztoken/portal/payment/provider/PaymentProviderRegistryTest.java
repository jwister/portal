package io.ztoken.portal.payment.provider;

import io.ztoken.portal.payment.domain.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentProviderRegistryTest {
    private final PaymentProvider paypal = () -> PaymentMethod.PAYPAL;
    private final PaymentProvider trc20 = () -> PaymentMethod.USDT_TRC20;

    @Test
    void resolvesAProviderByPaymentMethod() {
        PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of(paypal, trc20));

        assertThat(registry.require(PaymentMethod.PAYPAL)).isSameAs(paypal);
        assertThat(registry.require(PaymentMethod.USDT_TRC20)).isSameAs(trc20);
    }

    @Test
    void rejectsDuplicatePaymentMethodRegistration() {
        assertThatThrownBy(() -> new PaymentProviderRegistry(List.of(paypal, () -> PaymentMethod.PAYPAL)))
                .isInstanceOf(IllegalStateException.class);
    }
}
