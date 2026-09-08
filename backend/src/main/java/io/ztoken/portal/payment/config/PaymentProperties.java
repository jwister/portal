package io.ztoken.portal.payment.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "payment")
@Validated
public class PaymentProperties {

    private int orderExpiryMinutes = 30;
    private long quotaPerUsd = 500_000L;
    private final Paypal paypal = new Paypal();
    @Valid
    private final Trc20 trc20 = new Trc20();
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

    public Trc20 getTrc20() {
        return trc20;
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

    /** TRC20-USDT 渠道的链上核验、扫描和收款地址池配置。 */
    public static class Trc20 {

        private String usdtContract = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";
        private int confirmationCount = 20;
        private boolean amountSuffixEnabled = true;
        private long scanFixedDelayMs = 5_000L;
        private long txidRetryFixedDelayMs = 5_000L;
        private long orderExpiryFixedDelayMs = 5_000L;
        @Valid
        private final TronGrid trongrid = new TronGrid();
        private List<String> addresses = new ArrayList<>();

        public String getUsdtContract() { return usdtContract; }
        public void setUsdtContract(String usdtContract) { this.usdtContract = usdtContract; }
        public int getConfirmationCount() { return confirmationCount; }
        public void setConfirmationCount(int confirmationCount) { this.confirmationCount = confirmationCount; }
        public boolean isAmountSuffixEnabled() { return amountSuffixEnabled; }
        public void setAmountSuffixEnabled(boolean amountSuffixEnabled) { this.amountSuffixEnabled = amountSuffixEnabled; }
        public long getScanFixedDelayMs() { return scanFixedDelayMs; }
        public void setScanFixedDelayMs(long scanFixedDelayMs) { this.scanFixedDelayMs = scanFixedDelayMs; }
        public long getTxidRetryFixedDelayMs() { return txidRetryFixedDelayMs; }
        public void setTxidRetryFixedDelayMs(long txidRetryFixedDelayMs) { this.txidRetryFixedDelayMs = txidRetryFixedDelayMs; }
        public long getOrderExpiryFixedDelayMs() { return orderExpiryFixedDelayMs; }
        public void setOrderExpiryFixedDelayMs(long orderExpiryFixedDelayMs) { this.orderExpiryFixedDelayMs = orderExpiryFixedDelayMs; }
        public TronGrid getTrongrid() { return trongrid; }
        public List<String> getAddresses() { return List.copyOf(addresses); }
        public void setAddresses(List<String> addresses) { this.addresses = addresses == null ? new ArrayList<>() : new ArrayList<>(addresses); }
    }

    /** TronGrid 访问地址与服务端 API Key；Key 仅由后端读取。 */
    public static class TronGrid {
        private String baseUrl = "https://api.trongrid.io";
        private String apiKey;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
