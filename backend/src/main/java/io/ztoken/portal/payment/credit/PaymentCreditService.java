package io.ztoken.portal.payment.credit;

import io.ztoken.portal.payment.config.PaymentProperties;
import io.ztoken.portal.payment.domain.CreditAttempt;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentOrderStatus;
import io.ztoken.portal.payment.repository.CreditAttemptRepository;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;

@Service
public class PaymentCreditService {

    private final PaymentOrderRepository orders;
    private final CreditAttemptRepository attempts;
    private final NewApiCreditClient newApiCredit;
    private final PaymentProperties properties;
    private final TransactionTemplate transactions;

    public PaymentCreditService(PaymentOrderRepository orders, CreditAttemptRepository attempts,
                                NewApiCreditClient newApiCredit, PaymentProperties properties,
                                PlatformTransactionManager transactionManager) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.newApiCredit = Objects.requireNonNull(newApiCredit, "newApiCredit");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void creditConfirmedOrder(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return;
        }

        CreditWork work = transactions.execute(status -> claimConfirmedOrder(orderNo));
        if (work == null) {
            return;
        }

        CreditOutcome outcome = credit(work.order());
        transactions.executeWithoutResult(status -> finishCredit(work, outcome));
    }

    private CreditWork claimConfirmedOrder(String orderNo) {
        PaymentOrder order = orders.findByOrderNoForUpdate(orderNo).orElse(null);
        if (order == null || !order.startCrediting(Instant.now())) {
            return null;
        }
        CreditAttempt attempt = attempts.save(CreditAttempt.started(order, Instant.now()));
        return new CreditWork(order, attempt);
    }

    private void finishCredit(CreditWork work, CreditOutcome outcome) {
        CreditResult result = outcome.result();
        PaymentOrder order = orders.findByOrderNoForUpdate(work.order().getOrderNo()).orElse(null);
        if (order == null || order.getStatus() != PaymentOrderStatus.CREDITING) {
            return;
        }

        Instant finishedAt = Instant.now();
        switch (result) {
            case SUCCESS -> work.attempt().finish(CreditAttempt.Status.SUCCESS,
                    "NewAPI quota increase confirmed", finishedAt);
            case FAILED -> work.attempt().finish(CreditAttempt.Status.FAILED,
                    outcome.message() == null ? "NewAPI rejected quota increase" : outcome.message(), finishedAt);
            case UNKNOWN -> work.attempt().finish(CreditAttempt.Status.UNKNOWN,
                    "NewAPI quota increase result is unknown", finishedAt);
        }
        attempts.save(work.attempt());

        switch (result) {
            case SUCCESS -> order.markPaid(finishedAt);
            case FAILED -> order.markCreditFailed(finishedAt);
            case UNKNOWN -> order.markCreditUnknown(finishedAt);
        }
    }

    private CreditOutcome credit(PaymentOrder order) {
        if (order.getQuotaToCredit() > properties.getNewApiCredit().getMaxWalletQuota()) {
            return new CreditOutcome(CreditResult.FAILED,
                    "Payment quota exceeds the current NewAPI wallet limit");
        }
        return new CreditOutcome(callNewApi(order), null);
    }

    private CreditResult callNewApi(PaymentOrder order) {
        try {
            CreditResult result = newApiCredit.addQuota(order.getNewApiUserId(), order.getQuotaToCredit());
            return result == null ? CreditResult.UNKNOWN : result;
        } catch (RuntimeException exception) {
            return CreditResult.UNKNOWN;
        }
    }

    private record CreditWork(PaymentOrder order, CreditAttempt attempt) {
    }

    private record CreditOutcome(CreditResult result, String message) {
    }
}
