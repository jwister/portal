package io.ztoken.portal.payment.repository;

import io.ztoken.portal.payment.domain.PaymentAddress;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/** 地址池查询必须带行锁，避免并发订单选择到相同的可用尾数。 */
public interface PaymentAddressRepository extends JpaRepository<PaymentAddress, Long> {

    Optional<PaymentAddress> findByAddress(String address);

    List<PaymentAddress> findByEnabledTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select address from PaymentAddress address where address.enabled = true order by address.activeOrderCount asc, address.id asc")
    List<PaymentAddress> lockEnabledOrderedByLoad();
}
