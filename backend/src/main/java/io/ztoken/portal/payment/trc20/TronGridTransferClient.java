package io.ztoken.portal.payment.trc20;

/** 可替换的 TronGrid 查询边界，便于 TxID 核验和扫描共享同一数据源。 */
public interface TronGridTransferClient {
    TransferQueryResult findByTxid(String txid);
}
