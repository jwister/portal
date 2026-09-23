package io.ztoken.portal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "portal")
public class PortalProperties {

    private String sessionKey;
    private Duration sessionTtl = Duration.ofDays(7);
    private boolean sessionSecureCookie;
    private NewApi newApi = new NewApi();
    private String publicApiUrl;

    public String getPublicApiUrl() { return publicApiUrl; }
    public void setPublicApiUrl(String publicApiUrl) { this.publicApiUrl = publicApiUrl; }

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
        private String accessToken;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getPricingToken() {
            return accessToken;
        }

        public void setPricingToken(String pricingToken) {
            if (this.accessToken == null || this.accessToken.isBlank()) {
                this.accessToken = pricingToken;
            }
        }

        public String getAdminToken() {
            return accessToken;
        }

        public void setAdminToken(String adminToken) {
            if (this.accessToken == null || this.accessToken.isBlank()) {
                this.accessToken = adminToken;
            }
        }
    }
}

