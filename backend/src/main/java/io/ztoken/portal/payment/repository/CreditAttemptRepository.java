package io.ztoken.portal.payment.repository;

import io.ztoken.portal.payment.domain.CreditAttempt;
import io.ztoken.portal.payment.domain.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditAttemptRepository extends JpaRepository<CreditAttempt, Long> {

    List<CreditAttempt> findByPaymentOrderOrderByCreatedAtDesc(PaymentOrder paymentOrder);
}
