package io.ztoken.portal.payment.provider;

import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentOrder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentProviderRegistryTest {
    private final PaymentProvider paypal = provider(PaymentMethod.PAYPAL);
    private final PaymentProvider trc20 = provider(PaymentMethod.USDT_TRC20);

    @Test
    void resolvesAProviderByPaymentMethod() {
        PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of(paypal, trc20));

        assertThat(registry.require(PaymentMethod.PAYPAL)).isSameAs(paypal);
        assertThat(registry.require(PaymentMethod.USDT_TRC20)).isSameAs(trc20);
    }

    @Test
    void rejectsDuplicatePaymentMethodRegistration() {
        assertThatThrownBy(() -> new PaymentProviderRegistry(List.of(paypal, provider(PaymentMethod.PAYPAL))))
                .isInstanceOf(IllegalStateException.class);
    }

    private static PaymentProvider provider(PaymentMethod method) {
        return new PaymentProvider() {
            @Override public PaymentMethod method() { return method; }
            @Override public PaymentOrder createOrder(long userId, long amountUsdMinor, long quotaToCredit,
                                                      java.time.Instant createdAt, java.time.Instant expiresAt) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
