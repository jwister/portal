package io.ztoken.portal.payment.repository;

import io.ztoken.portal.payment.domain.PaymentScanCursor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentScanCursorRepository extends JpaRepository<PaymentScanCursor, Long> {
    Optional<PaymentScanCursor> findByProviderAndPaymentAddressId(String provider, long paymentAddressId);
}
