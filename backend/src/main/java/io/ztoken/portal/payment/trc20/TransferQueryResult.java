package io.ztoken.portal.payment.trc20;

import java.util.List;

/** 区分链上服务故障与已成功查询但尚未发现可匹配转账。 */
public record TransferQueryResult(List<ObservedTransfer> transfers, String failureReason) {
    public static TransferQueryResult success(List<ObservedTransfer> transfers) { return new TransferQueryResult(List.copyOf(transfers), null); }
    public static TransferQueryResult retryableFailure(String reason) { return new TransferQueryResult(List.of(), reason); }
    public boolean succeeded() { return failureReason == null; }
}
