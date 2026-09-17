package io.ztoken.portal.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "payment_orders")
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, updatable = false, length = 48)
    private String orderNo;

    @Column(name = "newapi_user_id", nullable = false, updatable = false)
    private long newApiUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false, length = 32)
    private PaymentMethod paymentMethod;

    @Column(name = "amount_usd_minor", nullable = false, updatable = false)
    private long amountUsdMinor;

    @Column(name = "quota_to_credit", nullable = false, updatable = false)
    private long quotaToCredit;

    /** TRC20 地址池关联；PayPal 订单不分配链上地址。 */
    @ManyToOne
    @JoinColumn(name = "payment_address_id")
    private PaymentAddress paymentAddress;

    /** 下单时固化的收款地址，避免地址池记录后续变动影响历史订单。 */
    @Column(name = "receive_address", length = 64)
    private String receiveAddress;

    /** TRC20 按 USDT 六位最小单位保存；新订单收款金额固定展示为两位小数。 */
    @Column(name = "payable_minor")
    private Long payableMinor;

    @Column(name = "payable_currency", length = 8)
    private String payableCurrency;

    @Column(name = "payable_scale")
    private Integer payableScale;

    @Column(name = "submitted_txid", length = 128)
    private String submittedTxid;

    @Column(name = "txid_check_count", nullable = false)
    private int txidCheckCount;

    @Column(name = "last_txid_checked_at")
    private Instant lastTxidCheckedAt;

    @Column(name = "next_txid_check_at")
    private Instant nextTxidCheckAt;

    @Column(name = "last_txid_check_result", length = 64)
    private String lastTxidCheckResult;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentOrderStatus status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "credited_at")
    private Instant creditedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentOrder() {
    }

    private PaymentOrder(String orderNo, long newApiUserId, PaymentMethod paymentMethod, long amountUsdMinor,
                         long quotaToCredit, Instant createdAt, Instant expiresAt) {
        this.orderNo = requireText(orderNo, "orderNo");
        this.newApiUserId = newApiUserId;
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "paymentMethod");
        this.amountUsdMinor = amountUsdMinor;
        this.quotaToCredit = quotaToCredit;
        this.status = PaymentOrderStatus.WAITING_PAYMENT;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public static PaymentOrder paypal(String orderNo, long newApiUserId, long amountUsdMinor, long quotaToCredit,
                                      Instant createdAt, Instant expiresAt) {
        return new PaymentOrder(orderNo, newApiUserId, PaymentMethod.PAYPAL, amountUsdMinor, quotaToCredit,
                createdAt, expiresAt);
    }

    /** 创建仅由服务端地址池和金额识别码决定的 TRC20-USDT 支付订单。 */
    public static PaymentOrder usdtTrc20(String orderNo, long newApiUserId, long amountUsdMinor, long quotaToCredit,
                                         PaymentAddress paymentAddress, long payableMinor, Instant createdAt, Instant expiresAt) {
        PaymentOrder order = new PaymentOrder(orderNo, newApiUserId, PaymentMethod.USDT_TRC20, amountUsdMinor,
                quotaToCredit, createdAt, expiresAt);
        order.paymentAddress = Objects.requireNonNull(paymentAddress, "paymentAddress");
        order.receiveAddress = paymentAddress.getAddress();
        order.payableMinor = payableMinor;
        order.payableCurrency = "USDT";
        order.payableScale = 2;
        return order;
    }

    public boolean confirm(Instant now) {
        Instant transitionTime = Objects.requireNonNull(now, "now");
        if (status != PaymentOrderStatus.WAITING_PAYMENT) {
            return false;
        }
        if (!expiresAt.isAfter(transitionTime)) {
            status = PaymentOrderStatus.EXPIRED;
            releaseTrc20AddressLoad();
            updatedAt = transitionTime;
            return false;
        }
        status = PaymentOrderStatus.CONFIRMED;
        releaseTrc20AddressLoad();
        confirmedAt = transitionTime;
        updatedAt = transitionTime;
        return true;
    }

    public boolean expireIfPast(Instant now) {
        Instant transitionTime = Objects.requireNonNull(now, "now");
        if (status != PaymentOrderStatus.WAITING_PAYMENT || expiresAt.isAfter(transitionTime)) {
            return false;
        }
        status = PaymentOrderStatus.EXPIRED;
        releaseTrc20AddressLoad();
        updatedAt = transitionTime;
        return true;
    }

    public boolean cancel(Instant now) {
        Instant transitionTime = Objects.requireNonNull(now, "now");
        if (status != PaymentOrderStatus.WAITING_PAYMENT) {
            return false;
        }
        if (!expiresAt.isAfter(transitionTime)) {
            status = PaymentOrderStatus.EXPIRED;
            releaseTrc20AddressLoad();
            updatedAt = transitionTime;
            return false;
        }
        status = PaymentOrderStatus.CANCELLED;
        releaseTrc20AddressLoad();
        updatedAt = transitionTime;
        return true;
    }

    public boolean startCrediting(Instant now) {
        Instant transitionTime = Objects.requireNonNull(now, "now");
        if (status != PaymentOrderStatus.CONFIRMED) {
            return false;
        }
        status = PaymentOrderStatus.CREDITING;
        updatedAt = transitionTime;
        return true;
    }

    public boolean markPaid(Instant now) {
        Instant transitionTime = Objects.requireNonNull(now, "now");
        if (status != PaymentOrderStatus.CREDITING) {
            return false;
        }
        status = PaymentOrderStatus.PAID;
        creditedAt = transitionTime;
        updatedAt = transitionTime;
        return true;
    }

    public boolean markCreditFailed(Instant now) {
        return finishCredit(PaymentOrderStatus.CREDIT_FAILED, now);
    }

    public boolean markCreditUnknown(Instant now) {
        return finishCredit(PaymentOrderStatus.CREDIT_UNKNOWN, now);
    }

    private boolean finishCredit(PaymentOrderStatus finalStatus, Instant now) {
        Instant transitionTime = Objects.requireNonNull(now, "now");
        if (status != PaymentOrderStatus.CREDITING) {
            return false;
        }
        status = finalStatus;
        updatedAt = transitionTime;
        return true;
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public long getNewApiUserId() {
        return newApiUserId;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public long getAmountUsdMinor() {
        return amountUsdMinor;
    }

    public long getQuotaToCredit() {
        return quotaToCredit;
    }

    public PaymentAddress getPaymentAddress() { return paymentAddress; }
    public String getReceiveAddress() { return receiveAddress; }
    public Long getPayableMinor() { return payableMinor; }
    public String getPayableCurrency() { return payableCurrency; }
    public Integer getPayableScale() { return payableScale; }

    /** 只有仍在等待链上付款的 TRC20 订单可以由扫描或 TxID 查询确认。 */
    public boolean isWaitingForTrc20Payment() {
        return paymentMethod == PaymentMethod.USDT_TRC20 && status == PaymentOrderStatus.WAITING_PAYMENT;
    }

    /** 保存用户提交的 TxID；链上结果始终由服务端查询后更新。 */
    public void submitTxid(String txid, Instant now) {
        if (!isWaitingForTrc20Payment() || txid == null || txid.isBlank()) throw new IllegalArgumentException("TxID 无效");
        this.submittedTxid = txid.trim();
        this.lastTxidCheckResult = "SUBMITTED";
        this.txidCheckCount = 0;
        this.lastTxidCheckedAt = null;
        this.nextTxidCheckAt = now;
        this.updatedAt = now;
    }

    /** 记录下一次链上检查，防止节点索引或确认数延迟导致人工重复提交。 */
    public void scheduleTxidRetry(Instant nextCheckAt, String result, Instant checkedAt) {
        this.txidCheckCount++;
        this.lastTxidCheckedAt = Objects.requireNonNull(checkedAt, "checkedAt");
        this.nextTxidCheckAt = Objects.requireNonNull(nextCheckAt, "nextCheckAt");
        this.lastTxidCheckResult = requireText(result, "result");
        this.updatedAt = checkedAt;
    }

    public void finishTxidCheck(String result, Instant now) {
        this.lastTxidCheckedAt = now;
        this.nextTxidCheckAt = null;
        this.lastTxidCheckResult = requireText(result, "result");
        this.updatedAt = now;
    }
    public String getSubmittedTxid() { return submittedTxid; }
    public int getTxidCheckCount() { return txidCheckCount; }
    public Instant getLastTxidCheckedAt() { return lastTxidCheckedAt; }
    public Instant getNextTxidCheckAt() { return nextTxidCheckAt; }
    public String getLastTxidCheckResult() { return lastTxidCheckResult; }

    /** 确认或过期只会离开待支付态一次，因此地址负载恰好释放一次。 */
    private void releaseTrc20AddressLoad() {
        if (paymentMethod == PaymentMethod.USDT_TRC20 && paymentAddress != null) paymentAddress.decrementActiveOrderCount();
    }

    public PaymentOrderStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getCreditedAt() {
        return creditedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
