package io.ztoken.portal.payment.provider;

import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentOrder;

import java.time.Instant;

/** 支付渠道最小注册契约；新增渠道只需声明其唯一方式并注册实现。 */
public interface PaymentProvider {
    PaymentMethod method();

    /** 使用服务端已校验的订单参数创建并持久化渠道本地订单。 */
    PaymentOrder createOrder(long userId, long amountUsdMinor, long quotaToCredit, Instant createdAt, Instant expiresAt);
}
