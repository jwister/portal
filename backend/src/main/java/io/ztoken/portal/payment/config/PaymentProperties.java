package io.ztoken.portal.payment.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "payment")
@Validated
public class PaymentProperties {

    private int orderExpiryMinutes = 30;
    private long quotaPerUsd = 500_000L;
    private final Paypal paypal = new Paypal();
    @Valid
    private final NewApiCredit newApiCredit = new NewApiCredit();

    public int getOrderExpiryMinutes() {
        return orderExpiryMinutes;
    }

    public void setOrderExpiryMinutes(int orderExpiryMinutes) {
        this.orderExpiryMinutes = orderExpiryMinutes;
    }

    public long getQuotaPerUsd() {
        return quotaPerUsd;
    }

    public void setQuotaPerUsd(long quotaPerUsd) {
        this.quotaPerUsd = quotaPerUsd;
    }

    public Paypal getPaypal() {
        return paypal;
    }

    public NewApiCredit getNewApiCredit() {
        return newApiCredit;
    }

    public static class Paypal {

        private String mode = "sandbox";
        private String clientId;
        private String clientSecret;
        private String webhookId;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getWebhookId() {
            return webhookId;
        }

        public void setWebhookId(String webhookId) {
            this.webhookId = webhookId;
        }

        public boolean isConfigured() {
            return hasText(clientId) && hasText(clientSecret) && hasText(webhookId);
        }
    }

    public static class NewApiCredit {

        private String accessToken;
        @Positive
        @Max(9_007_199_254_740_991L)
        private long maxWalletQuota = 2_147_483_647L;

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public long getMaxWalletQuota() {
            return maxWalletQuota;
        }

        public void setMaxWalletQuota(long maxWalletQuota) {
            this.maxWalletQuota = maxWalletQuota;
        }

        public boolean isConfigured() {
            return hasText(accessToken);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
