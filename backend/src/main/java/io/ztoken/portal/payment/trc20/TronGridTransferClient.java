package io.ztoken.portal.payment.trc20;

/** 可替换的 TronGrid 查询边界；链上核验仅按用户提交的 TxID 进行。 */
public interface TronGridTransferClient {
    TransferQueryResult findByTxid(String txid);
}
