package io.ztoken.portal.payment.repository;

import io.ztoken.portal.payment.domain.PaymentAddress;
import io.ztoken.portal.payment.domain.PaymentAmountRegistry;
import org.springframework.data.jpa.repository.JpaRepository;

/** 地址金额占用记录的唯一约束由数据库和查询共同保护。 */
public interface PaymentAmountRegistryRepository extends JpaRepository<PaymentAmountRegistry, Long> {

    boolean existsByPaymentAddressAndPayableMinor(PaymentAddress paymentAddress, long payableMinor);
}
