package io.ztoken.portal.payment.provider;

import io.ztoken.portal.payment.domain.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 以支付方式查找渠道，启动时拒绝重复注册以避免错误路由收款。 */
@Component
public class PaymentProviderRegistry {
    private final Map<PaymentMethod, PaymentProvider> providers = new EnumMap<>(PaymentMethod.class);

    public PaymentProviderRegistry(List<PaymentProvider> providers) {
        for (PaymentProvider provider : providers) {
            PaymentProvider previous = this.providers.putIfAbsent(Objects.requireNonNull(provider, "provider").method(), provider);
            if (previous != null) throw new IllegalStateException("重复注册支付渠道：" + provider.method());
        }
    }

    public PaymentProvider require(PaymentMethod method) {
        PaymentProvider provider = providers.get(Objects.requireNonNull(method, "method"));
        if (provider == null) throw new IllegalStateException("未注册的支付渠道：" + method);
        return provider;
    }
}
