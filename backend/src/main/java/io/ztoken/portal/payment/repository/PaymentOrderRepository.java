package io.ztoken.portal.payment.repository;

import io.ztoken.portal.payment.domain.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByOrderNo(String orderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select paymentOrder from PaymentOrder paymentOrder where paymentOrder.orderNo = :orderNo")
    Optional<PaymentOrder> findByOrderNoForUpdate(@Param("orderNo") String orderNo);

    List<PaymentOrder> findByNewApiUserIdOrderByCreatedAtDesc(long newApiUserId);
}
