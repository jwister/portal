package io.ztoken.portal.payment.provider;

import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.paypal.PayPalPaymentService;
import org.springframework.stereotype.Component;

/** PayPal 现有服务的渠道声明，保留既有 API 行为。 */
@Component
public class PayPalPaymentProvider implements PaymentProvider {
    @SuppressWarnings("unused") private final PayPalPaymentService service;
    public PayPalPaymentProvider(PayPalPaymentService service) { this.service = service; }
    @Override public PaymentMethod method() { return PaymentMethod.PAYPAL; }
}
