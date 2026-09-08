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

    @Test
    void trc20OrderRetainsItsExactServerAssignedPaymentInstruction() {
        PaymentAddress address = new PaymentAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", now);

        PaymentOrder order = PaymentOrder.usdtTrc20(
                "PO-TRON-1", 7L, 2_500L, 12_500_000L, address, 2_500_017L, now, now.plusSeconds(30 * 60));

        assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.USDT_TRC20);
        assertThat(order.getReceiveAddress()).isEqualTo(address.getAddress());
        assertThat(order.getPayableMinor()).isEqualTo(2_500_017L);
        assertThat(order.getPayableCurrency()).isEqualTo("USDT");
        assertThat(order.getPayableScale()).isEqualTo(6);
    }

    @Test
    void trc20OrderRecordsRetryScheduleAndReleasesItsAddressWhenCancelled() {
        PaymentAddress address = new PaymentAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", now);
        address.incrementActiveOrderCount();
        PaymentOrder order = PaymentOrder.usdtTrc20(
                "PO-TRON-2", 7L, 2_500L, 12_500_000L, address, 2_500_017L, now, now.plusSeconds(30 * 60));

        order.submitTxid("abc", now);
        order.scheduleTxidRetry(now.plusSeconds(5), "NOT_INDEXED", now.plusSeconds(1));

        assertThat(order.getTxidCheckCount()).isEqualTo(1);
        assertThat(order.getLastTxidCheckedAt()).isEqualTo(now.plusSeconds(1));
        assertThat(order.getNextTxidCheckAt()).isEqualTo(now.plusSeconds(5));
        assertThat(order.cancel(now.plusSeconds(2))).isTrue();
        assertThat(address.getActiveOrderCount()).isZero();
    }

    @Test
    void cancellationAtExpiryStillReleasesTheTrc20AddressLoad() {
        PaymentAddress address = new PaymentAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", now);
        address.incrementActiveOrderCount();
        PaymentOrder order = PaymentOrder.usdtTrc20("PO-TRON-3", 7L, 100L, 500_000L, address, 1_000_001L, now, now.plusSeconds(1));

        assertThat(order.cancel(now.plusSeconds(1))).isFalse();
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.EXPIRED);
        assertThat(address.getActiveOrderCount()).isZero();
    }
}
