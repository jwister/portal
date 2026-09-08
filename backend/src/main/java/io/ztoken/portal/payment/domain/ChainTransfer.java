package io.ztoken.portal.payment.domain;

import io.ztoken.portal.payment.trc20.ObservedTransfer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/** 已确认归属某张订单的链上转账事件；(txid, logIndex) 在数据库中只能使用一次。 */
@Entity
@Table(name = "chain_transfers")
public class ChainTransfer {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 128) private String txid;
    @Column(name = "log_index", nullable = false) private long logIndex;
    @Column(name = "contract_address", nullable = false, length = 64) private String contractAddress;
    @Column(name = "from_address", nullable = false, length = 64) private String fromAddress;
    @Column(name = "to_address", nullable = false, length = 64) private String toAddress;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(name = "block_number", nullable = false) private long blockNumber;
    @Column(name = "block_time", nullable = false) private Instant blockTime;
    @ManyToOne @JoinColumn(name = "payment_order_id") private PaymentOrder paymentOrder;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected ChainTransfer() {
    }

    /** 将已严格匹配的可确认事件写成不可复用的链上审计记录。 */
    public ChainTransfer(ObservedTransfer transfer, PaymentOrder paymentOrder, Instant createdAt) {
        this.txid = transfer.txid();
        this.logIndex = transfer.logIndex();
        this.contractAddress = transfer.contractAddress();
        this.fromAddress = transfer.fromAddress();
        this.toAddress = transfer.toAddress();
        this.amountMinor = transfer.amountMinor();
        this.blockNumber = transfer.blockNumber();
        this.blockTime = transfer.blockTime();
        this.paymentOrder = Objects.requireNonNull(paymentOrder, "paymentOrder");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }
}
