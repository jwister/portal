package io.ztoken.portal.payment.repository;

import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentProviderEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PaymentProviderEventRepositoryTest {

    @Autowired
    private PaymentProviderEventRepository events;

    @Autowired
    private PaymentOrderRepository orders;

    @BeforeEach
    void clearData() {
        events.deleteAll();
        orders.deleteAll();
    }

    @Test
    void insertsOnlyTheFirstDeliveryForOneProviderEventId() {
        Instant now = Instant.now();
        PaymentOrder order = orders.saveAndFlush(PaymentOrder.paypal(
                "PO-EVENT", 7L, 500L, 2_500_000L, now, now.plusSeconds(1_800)));

        int first = events.insertIfAbsent("PAYPAL", "WH-1", "PAYMENT.CAPTURE.COMPLETED", order.getId(), now,
                "Verified PayPal webhook");
        int duplicate = events.insertIfAbsent("PAYPAL", "WH-1", "PAYMENT.CAPTURE.COMPLETED", order.getId(), now,
                "Verified PayPal webhook");

        assertThat(first).isEqualTo(1);
        assertThat(duplicate).isZero();
        assertThat(events.findByProviderAndProviderEventId(io.ztoken.portal.payment.domain.PaymentMethod.PAYPAL, "WH-1"))
                .map(PaymentProviderEvent::getEventType)
                .contains("PAYMENT.CAPTURE.COMPLETED");
    }
}
