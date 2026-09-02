package io.ztoken.portal.payment.repository;

import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentProviderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentProviderEventRepository extends JpaRepository<PaymentProviderEvent, Long> {

    Optional<PaymentProviderEvent> findByProviderAndProviderEventId(PaymentMethod provider, String providerEventId);
}
