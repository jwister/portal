package io.ztoken.portal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "portal")
public class PortalProperties {

    private String sessionKey;
    private Duration sessionTtl = Duration.ofDays(7);
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

    public NewApi getNewApi() {
        return newApi;
    }

    public void setNewApi(NewApi newApi) {
        this.newApi = newApi;
    }

    public static class NewApi {
        private String baseUrl = "http://127.0.0.1:18081";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
