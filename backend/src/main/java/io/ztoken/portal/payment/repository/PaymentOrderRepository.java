package io.ztoken.portal.payment.repository;

import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByOrderNo(String orderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select paymentOrder from PaymentOrder paymentOrder where paymentOrder.orderNo = :orderNo")
    Optional<PaymentOrder> findByOrderNoForUpdate(@Param("orderNo") String orderNo);

    List<PaymentOrder> findByNewApiUserIdOrderByCreatedAtDesc(long newApiUserId);

    List<PaymentOrder> findByStatus(PaymentOrderStatus status);

    @Query("select paymentOrder from PaymentOrder paymentOrder where paymentOrder.status = io.ztoken.portal.payment.domain.PaymentOrderStatus.WAITING_PAYMENT and paymentOrder.submittedTxid is not null and paymentOrder.nextTxidCheckAt <= :now")
    List<PaymentOrder> findDueTxidChecks(@Param("now") Instant now);

    @Query("select paymentOrder from PaymentOrder paymentOrder where paymentOrder.status = io.ztoken.portal.payment.domain.PaymentOrderStatus.WAITING_PAYMENT and paymentOrder.expiresAt <= :now")
    List<PaymentOrder> findWaitingOrdersExpiredAt(@Param("now") Instant now);

    @Query("select paymentOrder from PaymentOrder paymentOrder where paymentOrder.status = io.ztoken.portal.payment.domain.PaymentOrderStatus.WAITING_PAYMENT and paymentOrder.receiveAddress = :receiveAddress and paymentOrder.payableMinor = :payableMinor")
    Optional<PaymentOrder> findWaitingByReceiveAddressAndPayableMinor(@Param("receiveAddress") String receiveAddress,
                                                                        @Param("payableMinor") long payableMinor);
}
