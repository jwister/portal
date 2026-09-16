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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

@Service
public class PaymentCreditService {

    private static final Logger log = LoggerFactory.getLogger(PaymentCreditService.class);

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
            log.info("NewAPI 额度入账无需执行：订单不存在、未确认或已被其他任务领取，订单号={}", orderNo);
            return;
        }

        log.info("NewAPI 额度入账任务已领取：订单号={}，用户ID={}，入账额度={}，订单状态={}",
                work.order().getOrderNo(), work.order().getNewApiUserId(), work.order().getQuotaToCredit(),
                work.order().getStatus());

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
        String message = switch (result) {
            case SUCCESS -> "NewAPI 额度入账成功";
            case FAILED -> "NewAPI 额度入账失败";
            case UNKNOWN -> "NewAPI 额度入账结果未知";
        };
        log.atLevel(result == CreditResult.SUCCESS ? org.slf4j.event.Level.INFO : org.slf4j.event.Level.WARN)
                .log("{}：订单号={}，用户ID={}，入账额度={}，订单状态={}", message,
                        order.getOrderNo(), order.getNewApiUserId(), order.getQuotaToCredit(), order.getStatus());
    }

    private CreditOutcome credit(PaymentOrder order) {
        if (order.getQuotaToCredit() > properties.getNewApiCredit().getMaxWalletQuota()) {
            log.warn("NewAPI 额度入账被钱包上限拦截：订单号={}，用户ID={}，入账额度={}，钱包上限={}",
                    order.getOrderNo(), order.getNewApiUserId(), order.getQuotaToCredit(),
                    properties.getNewApiCredit().getMaxWalletQuota());
            return new CreditOutcome(CreditResult.FAILED,
                    "Payment quota exceeds the current NewAPI wallet limit");
        }
        return new CreditOutcome(callNewApi(order), null);
    }

    private CreditResult callNewApi(PaymentOrder order) {
        try {
            log.info("正在调用 NewAPI 额度接口：订单号={}，用户ID={}，入账额度={}",
                    order.getOrderNo(), order.getNewApiUserId(), order.getQuotaToCredit());
            CreditResult result = newApiCredit.addQuota(order.getNewApiUserId(), order.getQuotaToCredit());
            return result == null ? CreditResult.UNKNOWN : result;
        } catch (RuntimeException exception) {
            log.error("调用 NewAPI 额度接口发生异常，结果按未知处理：订单号={}，用户ID={}，入账额度={}",
                    order.getOrderNo(), order.getNewApiUserId(), order.getQuotaToCredit());
            return CreditResult.UNKNOWN;
        }
    }

    private record CreditWork(PaymentOrder order, CreditAttempt attempt) {
    }

    private record CreditOutcome(CreditResult result, String message) {
    }
}
