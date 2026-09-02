package io.ztoken.portal.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    private int orderExpiryMinutes = 30;
    private long quotaPerUsd = 500_000L;
    private final Paypal paypal = new Paypal();
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

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public boolean isConfigured() {
            return hasText(accessToken);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
