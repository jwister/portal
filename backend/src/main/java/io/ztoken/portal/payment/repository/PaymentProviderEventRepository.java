package io.ztoken.portal.payment.repository;

import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentProviderEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PaymentProviderEventRepository extends JpaRepository<PaymentProviderEvent, Long> {

    Optional<PaymentProviderEvent> findByProviderAndProviderEventId(PaymentMethod provider, String providerEventId);

    /**
     * Atomically writes an audit record only for the first delivery of a provider event ID.
     * Both production MySQL and test H2 MySQL mode implement {@code INSERT IGNORE} for the
     * table's unique provider/event constraint.
     */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO payment_provider_events
                (provider, provider_event_id, event_type, payment_order_id, verified_at, audit_summary)
            VALUES (:provider, :eventId, :eventType, :paymentOrderId, :verifiedAt, :auditSummary)
            """, nativeQuery = true)
    int insertIfAbsent(@Param("provider") String provider, @Param("eventId") String eventId,
                       @Param("eventType") String eventType, @Param("paymentOrderId") Long paymentOrderId,
                       @Param("verifiedAt") Instant verifiedAt, @Param("auditSummary") String auditSummary);
}
