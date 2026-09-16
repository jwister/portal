package io.ztoken.portal.payment.credit;

import io.ztoken.portal.payment.domain.PaymentOrderStatus;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Repairs the narrow crash window after a payment confirmation commits but before its
 * AFTER_COMMIT listener starts crediting. Only still-CONFIRMED orders are eligible:
 * CREDITING and CREDIT_UNKNOWN are deliberately never retried automatically.
 */
@Component
public class PaymentCreditRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentCreditRecovery.class);

    private final PaymentOrderRepository orders;
    private final PaymentCreditService credits;

    public PaymentCreditRecovery(PaymentOrderRepository orders, PaymentCreditService credits) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.credits = Objects.requireNonNull(credits, "credits");
    }

    @Override
    public void run(ApplicationArguments args) {
        var confirmedOrders = orders.findByStatus(PaymentOrderStatus.CONFIRMED);
        log.info("支付入账恢复任务启动：待恢复确认订单数={}", confirmedOrders.size());
        for (var order : confirmedOrders) {
            if (order.getStatus() != PaymentOrderStatus.CONFIRMED) {
                continue;
            }
            try {
                log.info("支付入账恢复任务开始领取订单：订单号={}", order.getOrderNo());
                credits.creditConfirmedOrder(order.getOrderNo());
            } catch (RuntimeException exception) {
                // Continue with other confirmed orders; each claim remains guarded by the existing row lock.
                log.warn("支付入账恢复任务领取订单失败：订单号={}，异常类型={}",
                        order.getOrderNo(), exception.getClass().getSimpleName());
            }
        }
    }
}
