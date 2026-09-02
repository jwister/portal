package io.ztoken.portal.payment.config;

import io.ztoken.portal.PortalApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "payment.order-expiry-minutes=45",
        "payment.quota-per-usd=600000",
        "payment.paypal.mode=live",
        "payment.paypal.client-id=client-id",
        "payment.paypal.client-secret=client-secret",
        "payment.paypal.webhook-id=webhook-id",
        "payment.newapi-credit.access-token=credit-token",
        "payment.newapi-credit.max-wallet-quota=1000000"
})
class PaymentPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PaymentPropertiesConfiguration.class);

    @Autowired
    private PaymentProperties properties;

    @Test
    void bindsEnvironmentStylePaymentProperties() {
        assertThat(properties.getOrderExpiryMinutes()).isEqualTo(45);
        assertThat(properties.getQuotaPerUsd()).isEqualTo(600_000L);
        assertThat(properties.getPaypal().getMode()).isEqualTo("live");
        assertThat(properties.getPaypal().isConfigured()).isTrue();
        assertThat(properties.getNewApiCredit().isConfigured()).isTrue();
        assertThat(properties.getNewApiCredit().getMaxWalletQuota()).isEqualTo(1_000_000L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void rejectsANonPositiveNewApiWalletQuotaAtBinding(long maxWalletQuota) {
        contextRunner.withPropertyValues("payment.newapi-credit.max-wallet-quota=" + maxWalletQuota)
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PaymentProperties.class)
    static class PaymentPropertiesConfiguration {
    }
}
