package io.ztoken.portal.config;

import io.ztoken.portal.payment.config.PaymentProperties;
import io.ztoken.portal.newapi.NewApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/config.js")
public class SiteConfigController {

    private static final Logger log = LoggerFactory.getLogger(SiteConfigController.class);

    private final PortalProperties properties;
    private final PaymentProperties paymentProperties;
    private final NewApiClient newApiClient;

    public SiteConfigController(PortalProperties properties, PaymentProperties paymentProperties, NewApiClient newApiClient) {
        this.properties = properties;
        this.paymentProperties = paymentProperties;
        this.newApiClient = newApiClient;
    }

    @GetMapping(produces = "application/javascript")
    public String configJs() {
        String url = properties.getPublicApiUrl();
        if (url == null || url.isBlank()) {
            url = "https://api.ztoken.cc";
        } else if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        
        StringBuilder js = new StringBuilder();
        js.append("window.PORTAL_PUBLIC_API_URL = '").append(url).append("';\n");
        js.append("window.PORTAL_ENABLE_RECHARGE = ").append(paymentProperties.isEnabled()).append(";\n");

        try {
            JsonNode status = newApiClient.getSystemStatus();
            if (status != null && status.isObject()) {
                JsonNode data = status.has("data") ? status.path("data") : status;
                
                boolean displayInCurrency = data.path("display_in_currency").asBoolean(false);
                String customCurrencySymbol = data.path("custom_currency_symbol").asText("");
                double quotaPerUnit = data.path("quota_per_unit").asDouble(500000.0);
                double quotaForNewUser = data.path("quota_for_new_user").asDouble(0.0);

                if (!data.has("quota_per_unit")) {
                    quotaPerUnit = data.path("custom_currency_exchange_rate").asDouble(1.0);
                }

                js.append("window.PORTAL_DISPLAY_IN_CURRENCY = ").append(displayInCurrency).append(";\n");
                js.append("window.PORTAL_CURRENCY_SYMBOL = '").append(customCurrencySymbol).append("';\n");
                js.append("window.PORTAL_QUOTA_PER_USD = ").append(quotaPerUnit).append(";\n");
                js.append("window.PORTAL_QUOTA_FOR_NEW_USER = ").append(quotaForNewUser).append(";\n");
            }
        } catch (Exception e) {
            log.error("Failed to load new-api status for config.js", e);
        }

        return js.toString();
    }
}
