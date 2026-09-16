package io.ztoken.portal.payment.credit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Starts quota crediting only after the transaction that confirmed the payment has committed.
 * Provider services must publish {@link PaymentConfirmedEvent} from their confirming transaction.
 */
@Component
public class PaymentCreditListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentCreditListener.class);

    private final PaymentCreditService credits;

    public PaymentCreditListener(PaymentCreditService credits) {
        this.credits = Objects.requireNonNull(credits, "credits");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentConfirmed(PaymentConfirmedEvent event) {
        log.info("收到支付确认提交后的入账事件：订单号={}", event.orderNo());
        credits.creditConfirmedOrder(event.orderNo());
    }
}
