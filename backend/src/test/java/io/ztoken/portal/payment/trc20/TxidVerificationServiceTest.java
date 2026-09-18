package io.ztoken.portal.payment.trc20;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.ztoken.portal.payment.domain.PaymentAddress;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentOrderStatus;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.session.PortalPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TxidVerificationServiceTest {
    @Mock private PaymentOrderRepository orders;
    @Mock private TronGridTransferClient client;
    @Mock private TransferVerificationService verifier;

    @Test
    void doesNotQueryTronGridWhenNoOrderHasSubmittedATxid() {
        when(orders.findDueTxidChecks(any(Instant.class))).thenReturn(List.of());

        new TxidVerificationService(orders, client, verifier).retryDueTxids();

        verify(orders).findDueTxidChecks(any(Instant.class));
        verifyNoInteractions(client);
    }

    @Test
    void schedulesRetryWhenSubmittedTransactionIsNotYetIndexed() {
        Instant now = Instant.now();
        PaymentAddress address = new PaymentAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", now);
        PaymentOrder order = PaymentOrder.usdtTrc20("PO_TRON_1", 7L, 100L, 500_000L, address, 1_000_001L, now, now.plusSeconds(1_800));
        when(orders.findByOrderNoForUpdate("PO_TRON_1")).thenReturn(Optional.of(order));
        when(client.findByTxid("abc")).thenReturn(TransferQueryResult.success(List.of()));

        VerificationResult result = new TxidVerificationService(orders, client, verifier)
                .submit(new PortalPrincipal(7L, "user", "user@example.com"), "PO_TRON_1", "abc");

        assertThat(result).isEqualTo(VerificationResult.PENDING_CONFIRMATION);
        assertThat(order.getLastTxidCheckResult()).isEqualTo("NOT_INDEXED");
        assertThat(order.getTxidCheckCount()).isEqualTo(1);
        assertThat(order.getNextTxidCheckAt()).isAfter(order.getLastTxidCheckedAt());
    }

    @Test
    void recordsChineseDetailsWhenTransactionIsNotIndexedAndRetryIsScheduled() {
        Instant now = Instant.now();
        PaymentAddress address = new PaymentAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", now);
        PaymentOrder order = PaymentOrder.usdtTrc20("PO_TRON_1", 7L, 100L, 500_000L, address, 1_000_001L, now, now.plusSeconds(1_800));
        when(orders.findByOrderNoForUpdate("PO_TRON_1")).thenReturn(Optional.of(order));
        when(client.findByTxid("abc")).thenReturn(TransferQueryResult.success(List.of()));
        ListAppender<ILoggingEvent> appender = startAppender();

        new TxidVerificationService(orders, client, verifier)
                .submit(new PortalPrincipal(7L, "user", "user@example.com"), "PO_TRON_1", "abc");

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                        .contains("TRC20 交易暂未索引，已安排复查", "订单号=PO_TRON_1", "交易哈希=abc")
                        .doesNotContain("api-key"));
        stopAppender(appender);
    }

    @Test
    void recordsAmountMismatchWhenTransfersDoNotMatchOrderPayableAmount() {
        Instant now = Instant.now();
        PaymentAddress address = new PaymentAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", now);
        PaymentOrder order = PaymentOrder.usdtTrc20("PO_TRON_1", 7L, 100L, 500_000L, address, 1_000_001L, now, now.plusSeconds(1_800));
        when(orders.findByOrderNoForUpdate("PO_TRON_1")).thenReturn(Optional.of(order));

        ObservedTransfer transfer = new ObservedTransfer("abc", 0L, "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", "TFROM",
                order.getReceiveAddress(), 999_000L, 1L, now.plusSeconds(10), true, 20);
        when(client.findByTxid("abc")).thenReturn(TransferQueryResult.success(List.of(transfer)));
        when(verifier.verify(eq(order), eq(transfer), any(Instant.class))).thenReturn(VerificationResult.AMOUNT_MISMATCH);

        VerificationResult result = new TxidVerificationService(orders, client, verifier)
                .submit(new PortalPrincipal(7L, "user", "user@example.com"), "PO_TRON_1", "abc");

        assertThat(result).isEqualTo(VerificationResult.AMOUNT_MISMATCH);
        assertThat(order.getLastTxidCheckResult()).isEqualTo("AMOUNT_MISMATCH");
        assertThat(order.getNextTxidCheckAt()).isNull();
    }

    @Test
    void retryDueTxidsVerifiesAndConfirmsOrderEvenIfPastExpiresAt() {
        Instant past = Instant.now().minusSeconds(3_600);
        Instant expiredAt = past.plusSeconds(1_800); // 30 minutes ago
        Instant now = Instant.now();

        PaymentAddress address = new PaymentAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", past);
        PaymentOrder order = PaymentOrder.usdtTrc20("PO_EDGE", 7L, 100L, 500_000L, address, 1_000_001L, past, expiredAt);
        order.submitTxid("tx-edge-confirm", past.plusSeconds(1_700));
        order.scheduleTxidRetry(now.minusSeconds(1), "PENDING_CONFIRMATION", now.minusSeconds(5));

        ObservedTransfer transfer = new ObservedTransfer("tx-edge-confirm", 0L, "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", "TFROM",
                order.getReceiveAddress(), 1_000_001L, 1L, past.plusSeconds(1_710), true, 20);

        when(orders.findDueTxidChecks(any(Instant.class))).thenReturn(List.of(order));
        when(client.findByTxid("tx-edge-confirm")).thenReturn(TransferQueryResult.success(List.of(transfer)));
        when(verifier.verify(eq(order), eq(transfer), any(Instant.class))).thenAnswer(invocation -> {
            order.confirmVerified(now);
            return VerificationResult.CONFIRMED;
        });

        new TxidVerificationService(orders, client, verifier).retryDueTxids();

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CONFIRMED);
        assertThat(order.getLastTxidCheckResult()).isEqualTo("CONFIRMED");
    }

    @Test
    void expireDueOrdersSkipsOrdersActivelyRetryingTxid() {
        Instant now = Instant.now();
        Instant past = now.minusSeconds(2_000);
        PaymentAddress address = new PaymentAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", past);
        PaymentOrder order = PaymentOrder.usdtTrc20("PO_RETRYING", 7L, 100L, 500_000L, address, 1_000_001L, past, now.minusSeconds(10));
        order.submitTxid("tx-retrying", now.minusSeconds(20));
        order.scheduleTxidRetry(now.plusSeconds(10), "PENDING_CONFIRMATION", now);

        when(orders.findWaitingOrdersExpiredAt(any(Instant.class))).thenReturn(List.of(order));

        new TxidVerificationService(orders, client, verifier).expireDueOrders();

        // 依然保持 WAITING_PAYMENT，等待 retryDueTxids 处理
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.WAITING_PAYMENT);
    }

    @Test
    void expireDueOrdersClosesOrdersWithoutTxidOrFinishedCheck() {
        Instant now = Instant.now();
        Instant past = now.minusSeconds(2_000);
        PaymentAddress address = new PaymentAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", past);
        PaymentOrder orderNoTxid = PaymentOrder.usdtTrc20("PO_NO_TXID", 7L, 100L, 500_000L, address, 1_000_001L, past, now.minusSeconds(10));

        when(orders.findWaitingOrdersExpiredAt(any(Instant.class))).thenReturn(List.of(orderNoTxid));

        new TxidVerificationService(orders, client, verifier).expireDueOrders();

        assertThat(orderNoTxid.getStatus()).isEqualTo(PaymentOrderStatus.EXPIRED);
    }

    private static ListAppender<ILoggingEvent> startAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(TxidVerificationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void stopAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(TxidVerificationService.class);
        logger.detachAppender(appender);
        appender.stop();
    }
}
