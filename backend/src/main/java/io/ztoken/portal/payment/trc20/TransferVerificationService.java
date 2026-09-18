package io.ztoken.portal.payment.trc20;

import io.ztoken.portal.payment.config.PaymentProperties;
import io.ztoken.portal.payment.credit.PaymentConfirmedEvent;
import io.ztoken.portal.payment.domain.ChainTransfer;
import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentOrderStatus;
import io.ztoken.portal.payment.repository.ChainTransferRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

/** 以合约、地址、金额、时间窗口、确认数和事件唯一键核验 TRC20 转账。 */
@Service
public class TransferVerificationService {

    private static final Logger log = LoggerFactory.getLogger(TransferVerificationService.class);

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
        if (transfers.existsByTxidAndLogIndex(transfer.txid(), transfer.logIndex())) {
            log.info("TRC20 链上事件重复，已幂等跳过：订单号={}，交易哈希={}，日志索引={}", order.getOrderNo(), transfer.txid(), transfer.logIndex());
            return VerificationResult.DUPLICATE;
        }
        if (!transfer.executionSuccess() || !properties.getTrc20().getUsdtContract().equals(transfer.contractAddress())) {
            log.warn("TRC20 转账合约或执行状态不匹配：订单号={}，交易哈希={}，执行成功={}",
                    order.getOrderNo(), transfer.txid(), transfer.executionSuccess());
            return VerificationResult.UNMATCHED;
        }

        // 校验渠道与基础状态：支持 WAITING_PAYMENT 态以及因确认延迟跨过有效期的 EXPIRED 态订单进行真实入账
        boolean isTrc20Order = order.getPaymentMethod() == PaymentMethod.USDT_TRC20;
        boolean statusEligible = order.getStatus() == PaymentOrderStatus.WAITING_PAYMENT
                || order.getStatus() == PaymentOrderStatus.EXPIRED;
        if (!isTrc20Order || !statusEligible) {
            log.warn("TRC20 订单状态不满足核验条件：订单号={}，支付渠道={}，当前状态={}",
                    order.getOrderNo(), order.getPaymentMethod(), order.getStatus());
            return VerificationResult.UNMATCHED;
        }

        // 校验收款地址及交易发生时间窗口（以链上实际出块时间为准，必须在订单创建至过期时间之内）
        if (!order.getReceiveAddress().equals(transfer.toAddress())
                || transfer.blockTime().isBefore(order.getCreatedAt())
                || transfer.blockTime().isAfter(order.getExpiresAt())) {
            log.warn("TRC20 转账收款地址或链上出块时间窗口不匹配：订单号={}，交易哈希={}，出块时间={}，订单过期时间={}",
                    order.getOrderNo(), transfer.txid(), transfer.blockTime(), order.getExpiresAt());
            return VerificationResult.UNMATCHED;
        }

        // 校验转账金额：若转账地址与时间窗口合法，但金额不一致，细化返回 AMOUNT_MISMATCH
        if (order.getPayableMinor() != transfer.amountMinor()) {
            log.warn("TRC20 转账金额不匹配：订单号={}，交易哈希={}，订单应付最小单位={}，实际转账最小单位={}",
                    order.getOrderNo(), transfer.txid(), order.getPayableMinor(), transfer.amountMinor());
            return VerificationResult.AMOUNT_MISMATCH;
        }

        if (transfer.confirmations() < properties.getTrc20().getConfirmationCount()) {
            log.debug("TRC20 转账确认数不足：订单号={}，交易哈希={}，当前确认数={}，要求确认数={}",
                    order.getOrderNo(), transfer.txid(), transfer.confirmations(), properties.getTrc20().getConfirmationCount());
            return VerificationResult.PENDING_CONFIRMATION;
        }

        // 使用 confirmVerified 确认入账，即便订单核验时已超过过期时间也能安全恢复并确认入账，彻底杜绝吃单
        if (!order.confirmVerified(now)) {
            log.info("TRC20 订单状态已变化，确认操作幂等跳过：订单号={}，交易哈希={}，当前状态={}",
                    order.getOrderNo(), transfer.txid(), order.getStatus());
            return VerificationResult.DUPLICATE;
        }
        transfers.save(new ChainTransfer(transfer, order, now));
        if (events != null) events.publishEvent(new PaymentConfirmedEvent(order.getOrderNo()));
        log.info("TRC20 转账核验并确认支付成功：订单号={}，交易哈希={}，转账最小单位={}，确认数={}，订单状态={}",
                order.getOrderNo(), transfer.txid(), transfer.amountMinor(), transfer.confirmations(), order.getStatus());
        return VerificationResult.CONFIRMED;
    }
}
