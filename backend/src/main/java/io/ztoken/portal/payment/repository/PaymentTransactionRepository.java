package io.ztoken.portal.payment.repository;

import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByPaymentOrderAndProvider(PaymentOrder paymentOrder, PaymentMethod provider);

    Optional<PaymentTransaction> findByProviderAndProviderOrderId(PaymentMethod provider, String providerOrderId);
}
