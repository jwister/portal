package io.ztoken.portal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "portal")
public class PortalProperties {

    private String sessionKey;
    private Duration sessionTtl = Duration.ofDays(7);
    private boolean sessionSecureCookie;
    private NewApi newApi = new NewApi();

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public Duration getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(Duration sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    public boolean isSessionSecureCookie() {
        return sessionSecureCookie;
    }

    public void setSessionSecureCookie(boolean sessionSecureCookie) {
        this.sessionSecureCookie = sessionSecureCookie;
    }

    public NewApi getNewApi() {
        return newApi;
    }

    public void setNewApi(NewApi newApi) {
        this.newApi = newApi;
    }

    public static class NewApi {
        private String baseUrl;
        private String pricingToken;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getPricingToken() {
            return pricingToken;
        }

        public void setPricingToken(String pricingToken) {
            this.pricingToken = pricingToken;
        }
    }
}
