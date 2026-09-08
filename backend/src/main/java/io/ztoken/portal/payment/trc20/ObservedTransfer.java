package io.ztoken.portal.payment.trc20;

import java.time.Instant;

/** 从链上数据服务获得的单条 TRC20 Transfer 事件，尚未代表可入账的支付。 */
public record ObservedTransfer(String txid, long logIndex, String contractAddress, String fromAddress, String toAddress,
                               long amountMinor, long blockNumber, Instant blockTime, boolean executionSuccess,
                               int confirmations) {
}
