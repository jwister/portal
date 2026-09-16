package io.ztoken.portal.payment.trc20;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.ztoken.portal.payment.domain.PaymentAddress;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.session.PortalPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TxidVerificationServiceTest {
    @Mock private PaymentOrderRepository orders;
    @Mock private TronGridTransferClient client;
    @Mock private TransferVerificationService verifier;

    @Test
    void schedulesRetryWhenSubmittedTransactionIsNotYetIndexed() {
        Instant now = Instant.now();
        PaymentAddress address = new PaymentAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", now);
        PaymentOrder order = PaymentOrder.usdtTrc20("PO_TRON_1", 7L, 100L, 500_000L, address, 1_000_001L, now, now.plusSeconds(1_800));
        when(orders.findByOrderNoForUpdate("PO_TRON_1")).thenReturn(Optional.of(order));
        when(client.findByTxid("abc")).thenReturn(TransferQueryResult.success(java.util.List.of()));

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
        when(client.findByTxid("abc")).thenReturn(TransferQueryResult.success(java.util.List.of()));
        ListAppender<ILoggingEvent> appender = startAppender();

        new TxidVerificationService(orders, client, verifier)
                .submit(new PortalPrincipal(7L, "user", "user@example.com"), "PO_TRON_1", "abc");

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                        .contains("TRC20 交易暂未索引，已安排复查", "订单号=PO_TRON_1", "交易哈希=abc")
                        .doesNotContain("api-key"));
        stopAppender(appender);
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
