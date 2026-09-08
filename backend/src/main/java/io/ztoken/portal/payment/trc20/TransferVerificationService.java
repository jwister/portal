package io.ztoken.portal.payment.trc20;

import io.ztoken.portal.payment.config.PaymentProperties;
import io.ztoken.portal.payment.credit.PaymentConfirmedEvent;
import io.ztoken.portal.payment.domain.ChainTransfer;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.repository.ChainTransferRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Objects;

/** 以合约、地址、金额、时间窗口、确认数和事件唯一键核验 TRC20 转账。 */
@Service
public class TransferVerificationService {

    private final PaymentProperties properties;
    private final ChainTransferRepository transfers;
    private final ApplicationEventPublisher events;

    @Autowired
    public TransferVerificationService(PaymentProperties properties, ChainTransferRepository transfers,
                                       ApplicationEventPublisher events) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.transfers = Objects.requireNonNull(transfers, "transfers");
        this.events = events;
    }

    /** 供聚焦领域测试使用，不发布应用事件。 */
    TransferVerificationService(PaymentProperties properties, ChainTransferRepository transfers) {
        this(properties, transfers, null);
    }

    /** 仅在所有服务端可验证条件都成立时确认订单并永久占用链上事件。 */
    @Transactional
    public VerificationResult verify(PaymentOrder order, ObservedTransfer transfer, Instant now) {
        if (transfers.existsByTxidAndLogIndex(transfer.txid(), transfer.logIndex())) return VerificationResult.DUPLICATE;
        if (!transfer.executionSuccess() || !properties.getTrc20().getUsdtContract().equals(transfer.contractAddress())) {
            return VerificationResult.UNMATCHED;
        }
        if (!order.isWaitingForTrc20Payment() || !order.getReceiveAddress().equals(transfer.toAddress())
                || order.getPayableMinor() != transfer.amountMinor()
                || transfer.blockTime().isBefore(order.getCreatedAt()) || transfer.blockTime().isAfter(order.getExpiresAt())) {
            return VerificationResult.UNMATCHED;
        }
        if (transfer.confirmations() < properties.getTrc20().getConfirmationCount()) {
            return VerificationResult.PENDING_CONFIRMATION;
        }
        if (!order.confirm(now)) return VerificationResult.DUPLICATE;
        transfers.save(new ChainTransfer(transfer, order, now));
        if (events != null) events.publishEvent(new PaymentConfirmedEvent(order.getOrderNo()));
        return VerificationResult.CONFIRMED;
    }
}
