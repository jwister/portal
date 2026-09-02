package io.ztoken.portal.payment.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentPropertiesTest {

    @Test
    void defaultsToSandboxThirtyMinuteExpiryAndConfiguredQuotaRate() {
        PaymentProperties properties = new PaymentProperties();

        assertThat(properties.getPaypal().getMode()).isEqualTo("sandbox");
        assertThat(properties.getOrderExpiryMinutes()).isEqualTo(30);
        assertThat(properties.getQuotaPerUsd()).isEqualTo(500_000L);
    }

    @Test
    void paypalRequiresAllServerSideCredentials() {
        PaymentProperties properties = new PaymentProperties();
        PaymentProperties.Paypal paypal = properties.getPaypal();
        paypal.setClientId("client");

        assertThat(paypal.isConfigured()).isFalse();

        paypal.setClientSecret("secret");
        paypal.setWebhookId("webhook");

        assertThat(paypal.isConfigured()).isTrue();
    }

    @Test
    void newApiCreditRequiresANonBlankAccessToken() {
        PaymentProperties properties = new PaymentProperties();
        PaymentProperties.NewApiCredit newApiCredit = properties.getNewApiCredit();
        newApiCredit.setAccessToken("   ");

        assertThat(newApiCredit.isConfigured()).isFalse();

        newApiCredit.setAccessToken("access-token");

        assertThat(newApiCredit.isConfigured()).isTrue();
    }
}
