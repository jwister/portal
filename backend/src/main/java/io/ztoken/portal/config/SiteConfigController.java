package io.ztoken.portal.config;

import io.ztoken.portal.payment.config.PaymentProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config.js")
public class SiteConfigController {

    private final PortalProperties properties;
    private final PaymentProperties paymentProperties;

    public SiteConfigController(PortalProperties properties, PaymentProperties paymentProperties) {
        this.properties = properties;
        this.paymentProperties = paymentProperties;
    }

    @GetMapping(produces = "application/javascript")
    public String configJs() {
        String url = properties.getPublicApiUrl();
        if (url == null || url.isBlank()) {
            url = "https://api.ztoken.cc";
        } else if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return "window.PORTAL_PUBLIC_API_URL = '" + url + "';\n" +
               "window.PORTAL_ENABLE_RECHARGE = " + paymentProperties.isEnabled() + ";\n";
    }
}