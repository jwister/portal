package io.ztoken.portal.payment.repository;

import io.ztoken.portal.payment.domain.ChainTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

/** 以链上交易哈希及事件下标防止同一转账被多个订单复用。 */
public interface ChainTransferRepository extends JpaRepository<ChainTransfer, Long> {
    boolean existsByTxidAndLogIndex(String txid, long logIndex);
}
