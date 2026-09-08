package io.ztoken.portal.payment.trc20;

/** 可替换的 TronGrid 查询边界，便于 TxID 核验和扫描共享同一数据源。 */
public interface TronGridTransferClient {
    TransferQueryResult findByTxid(String txid);

    /** 返回一个地址近期的 TRC20 Transfer 事件，供后台地址池扫描匹配待支付订单。 */
    TransferQueryResult findByReceiveAddress(String receiveAddress, String fingerprint);
}
