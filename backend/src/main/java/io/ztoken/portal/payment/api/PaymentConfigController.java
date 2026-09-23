package io.ztoken.portal.payment.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/config")
public class PaymentConfigController {

    private final io.ztoken.portal.payment.config.PaymentProperties properties;

    public PaymentConfigController(io.ztoken.portal.payment.config.PaymentProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public java.util.Map<String, Boolean> config() {
        return java.util.Map.of("enabled", properties.isEnabled());
    }
}
