package io.ztoken.portal.payment.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentOrderTest {

    private final Instant now = Instant.parse("2026-09-02T10:00:00Z");

    @Test
    void confirmedOrderCanBeClaimedForExactlyOneCreditAttempt() {
        PaymentOrder order = PaymentOrder.paypal(
                "PO-1", 7L, 2_550L, 12_750_000L, now, now.plusSeconds(30));

        assertThat(order.confirm(now)).isTrue();
        assertThat(order.startCrediting(now.plusSeconds(1))).isTrue();
        assertThat(order.startCrediting(now.plusSeconds(2))).isFalse();
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CREDITING);
    }

    @Test
    void expiredWaitingOrderCannotBeConfirmed() {
        PaymentOrder order = PaymentOrder.paypal(
                "PO-2", 7L, 500L, 2_500_000L, now.minusSeconds(31 * 60), now.minusSeconds(1));

        assertThat(order.expireIfPast(now)).isTrue();
        assertThat(order.confirm(now)).isFalse();
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.EXPIRED);
    }

    @Test
    void onlyWaitingPaymentOrderCanBeCancelled() {
        PaymentOrder order = PaymentOrder.paypal(
                "PO-3", 7L, 500L, 2_500_000L, now, now.plusSeconds(30 * 60));

        assertThat(order.cancel(now)).isTrue();
        assertThat(order.confirm(now.plusSeconds(1))).isFalse();
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CANCELLED);
    }

    @Test
    void creditingOrderCanBeMarkedPaidOnce() {
        PaymentOrder order = PaymentOrder.paypal(
                "PO-4", 7L, 500L, 2_500_000L, now, now.plusSeconds(30 * 60));
        order.confirm(now);
        order.startCrediting(now.plusSeconds(1));

        assertThat(order.markPaid(now.plusSeconds(2))).isTrue();
        assertThat(order.markPaid(now.plusSeconds(3))).isFalse();
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(order.getCreditedAt()).isEqualTo(now.plusSeconds(2));
    }
}
