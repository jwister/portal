package io.ztoken.portal.payment.provider;

import io.ztoken.portal.payment.domain.PaymentMethod;

/** 支付渠道最小注册契约；新增渠道只需声明其唯一方式并注册实现。 */
public interface PaymentProvider {
    PaymentMethod method();
}
