package io.ztoken.portal.payment.trc20;

import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.session.PortalPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** 客户提交 TxID 后立即查询链上；不足确认数保持待支付，绝不提前入账。 */
@Service
public class TxidVerificationService {
    private static final Duration[] RETRY_DELAYS = {
            Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(15),
            Duration.ofSeconds(30), Duration.ofSeconds(60)
    };
    private final PaymentOrderRepository orders;
    private final TronGridTransferClient client;
    private final TransferVerificationService verifier;

    public TxidVerificationService(PaymentOrderRepository orders, TronGridTransferClient client, TransferVerificationService verifier) {
        this.orders = Objects.requireNonNull(orders, "orders"); this.client = Objects.requireNonNull(client, "client"); this.verifier = Objects.requireNonNull(verifier, "verifier");
    }

    @Transactional
    public VerificationResult submit(PortalPrincipal principal, String orderNo, String txid) {
        PaymentOrder order = orders.findByOrderNoForUpdate(orderNo).filter(item -> item.getNewApiUserId() == principal.userId()).orElseThrow();
        order.submitTxid(txid, Instant.now());
        return check(order, Instant.now());
    }

    /** 用户已提交但尚未索引或确认完成的交易按退避序列自动复查。 */
    @Scheduled(fixedDelayString = "${payment.trc20.txid-retry-fixed-delay-ms:5000}")
    @Transactional
    public void retryDueTxids() {
        Instant now = Instant.now();
        for (PaymentOrder order : orders.findDueTxidChecks(now)) {
            if (order.expireIfPast(now)) continue;
            check(order, now);
        }
    }

    /** 未提交 TxID 的超时订单也必须关闭，以回收地址池负载。 */
    @Scheduled(fixedDelayString = "${payment.trc20.order-expiry-fixed-delay-ms:5000}")
    @Transactional
    public void expireDueOrders() {
        Instant now = Instant.now();
        for (PaymentOrder order : orders.findWaitingOrdersExpiredAt(now)) order.expireIfPast(now);
    }

    private VerificationResult check(PaymentOrder order, Instant now) {
        TransferQueryResult query = client.findByTxid(order.getSubmittedTxid());
        if (!query.succeeded()) return scheduleRetry(order, now, "QUERY_FAILED");
        if (query.transfers().isEmpty()) return scheduleRetry(order, now, "NOT_INDEXED");
        boolean pending = false;
        boolean duplicate = false;
        for (ObservedTransfer transfer : query.transfers()) {
            VerificationResult result = verifier.verify(order, transfer, now);
            if (result == VerificationResult.CONFIRMED) { order.finishTxidCheck(result.name(), now); return result; }
            if (result == VerificationResult.PENDING_CONFIRMATION) pending = true;
            if (result == VerificationResult.DUPLICATE) duplicate = true;
        }
        if (pending) return scheduleRetry(order, now, "PENDING_CONFIRMATION");
        if (duplicate) { order.finishTxidCheck("DUPLICATE", now); return VerificationResult.DUPLICATE; }
        order.finishTxidCheck("UNMATCHED", now);
        return VerificationResult.UNMATCHED;
    }

    private VerificationResult scheduleRetry(PaymentOrder order, Instant now, String result) {
        Duration delay = RETRY_DELAYS[Math.min(Math.max(order.getTxidCheckCount(), 0), RETRY_DELAYS.length - 1)];
        order.scheduleTxidRetry(now.plus(delay), result, now);
        return VerificationResult.PENDING_CONFIRMATION;
    }
}
