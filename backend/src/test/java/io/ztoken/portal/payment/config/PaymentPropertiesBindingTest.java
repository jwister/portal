package io.ztoken.portal.payment.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "payment.order-expiry-minutes=45",
        "payment.quota-per-usd=600000",
        "payment.paypal.mode=live",
        "payment.paypal.client-id=client-id",
        "payment.paypal.client-secret=client-secret",
        "payment.paypal.webhook-id=webhook-id",
        "payment.newapi-credit.access-token=credit-token"
})
class PaymentPropertiesBindingTest {

    @Autowired
    private PaymentProperties properties;

    @Test
    void bindsEnvironmentStylePaymentProperties() {
        assertThat(properties.getOrderExpiryMinutes()).isEqualTo(45);
        assertThat(properties.getQuotaPerUsd()).isEqualTo(600_000L);
        assertThat(properties.getPaypal().getMode()).isEqualTo("live");
        assertThat(properties.getPaypal().isConfigured()).isTrue();
        assertThat(properties.getNewApiCredit().isConfigured()).isTrue();
    }
}
