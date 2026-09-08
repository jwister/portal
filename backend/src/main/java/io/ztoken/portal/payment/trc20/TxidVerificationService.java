package io.ztoken.portal.payment.trc20;

import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.session.PortalPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/** 客户提交 TxID 后立即查询链上；不足确认数保持待支付，绝不提前入账。 */
@Service
public class TxidVerificationService {
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
        TransferQueryResult query = client.findByTxid(txid);
        if (!query.succeeded()) { order.finishTxidCheck("QUERY_FAILED", Instant.now()); return VerificationResult.PENDING_CONFIRMATION; }
        if (query.transfers().isEmpty()) { order.finishTxidCheck("NOT_INDEXED", Instant.now()); return VerificationResult.PENDING_CONFIRMATION; }
        for (ObservedTransfer transfer : query.transfers()) {
            VerificationResult result = verifier.verify(order, transfer, Instant.now());
            if (result == VerificationResult.CONFIRMED || result == VerificationResult.PENDING_CONFIRMATION) { order.finishTxidCheck(result.name(), Instant.now()); return result; }
        }
        order.finishTxidCheck("UNMATCHED", Instant.now());
        return VerificationResult.UNMATCHED;
    }
}
