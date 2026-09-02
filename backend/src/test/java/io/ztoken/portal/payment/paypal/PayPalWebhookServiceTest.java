package io.ztoken.portal.payment.paypal;

import io.ztoken.portal.payment.credit.PaymentConfirmedEvent;
import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentOrderStatus;
import io.ztoken.portal.payment.domain.PaymentTransaction;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.payment.repository.PaymentProviderEventRepository;
import io.ztoken.portal.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayPalWebhookServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T10:00:00Z");

    @Mock
    private PayPalClient payPal;

    @Mock
    private PaymentProviderEventRepository events;

    @Mock
    private PaymentTransactionRepository transactions;

    @Mock
    private PaymentOrderRepository orders;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PayPalWebhookService service;

    @BeforeEach
    void setUp() {
        service = new PayPalWebhookService(payPal, events, transactions, orders, eventPublisher);
    }

    @Test
    void rejectsAnUnverifiedWebhookBeforeReadingOrPersistingItsEvent() {
        when(payPal.verifyWebhook(headers(), completedCaptureEvent("WH-1"))).thenReturn(false);

        assertThatThrownBy(() -> service.handle(headers(), completedCaptureEvent("WH-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature");

        verifyNoInteractions(events, transactions, orders, eventPublisher);
    }

    @Test
    void confirmsOneMatchingSignedCaptureAndDeduplicatesTheSameEventId() {
        PaymentOrder order = waitingOrder();
        PaymentTransaction transaction = PaymentTransaction.paypal(order, "PP-1", "PO-1-paypal", NOW);
        String body = completedCaptureEvent("WH-1");
        when(payPal.verifyWebhook(headers(), body)).thenReturn(true);
        when(transactions.findByProviderAndProviderOrderId(PaymentMethod.PAYPAL, "PP-1"))
                .thenReturn(Optional.of(transaction));
        when(orders.findByOrderNoForUpdate("PO-1")).thenReturn(Optional.of(order));
        when(events.insertIfAbsent(eq(PaymentMethod.PAYPAL.name()), eq("WH-1"),
                eq("PAYMENT.CAPTURE.COMPLETED"), any(), any(), any())).thenReturn(1, 0);

        service.handle(headers(), body);
        service.handle(headers(), body);

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CONFIRMED);
        assertThat(transaction.getProviderCaptureId()).isEqualTo("CAPTURE-1");
        verify(eventPublisher).publishEvent(new PaymentConfirmedEvent("PO-1"));
    }

    @ParameterizedTest
    @MethodSource("invalidCompletedCaptures")
    void refusesSignedCaptureUnlessCaptureIdStatusCurrencyAndExactCentsMatch(CaptureResource capture) {
        PaymentOrder order = waitingOrder();
        PaymentTransaction transaction = PaymentTransaction.paypal(order, "PP-1", "PO-1-paypal", NOW);
        String body = completedCaptureEvent("WH-1", capture);
        when(payPal.verifyWebhook(headers(), body)).thenReturn(true);
        when(transactions.findByProviderAndProviderOrderId(PaymentMethod.PAYPAL, "PP-1"))
                .thenReturn(Optional.of(transaction));
        when(orders.findByOrderNoForUpdate("PO-1")).thenReturn(Optional.of(order));
        when(events.insertIfAbsent(eq(PaymentMethod.PAYPAL.name()), eq("WH-1"),
                eq("PAYMENT.CAPTURE.COMPLETED"), any(), any(), any())).thenReturn(1);

        assertThatThrownBy(() -> service.handle(headers(), body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.WAITING_PAYMENT);
        assertThat(transaction.getProviderCaptureId()).isNull();
        verify(eventPublisher, never()).publishEvent(any());
    }

    private static Stream<CaptureResource> invalidCompletedCaptures() {
        return Stream.of(
                new CaptureResource("", "COMPLETED", "USD", "25.50"),
                new CaptureResource("CAPTURE-1", "PENDING", "USD", "25.50"),
                new CaptureResource("CAPTURE-1", "COMPLETED", "EUR", "25.50"),
                new CaptureResource("CAPTURE-1", "COMPLETED", "USD", "25.49")
        );
    }

    @Test
    void rejectsASignedCaptureWhoseIdConflictsWithTheExistingLocalCapture() {
        PaymentOrder order = waitingOrder();
        order.confirm(NOW);
        PaymentTransaction transaction = PaymentTransaction.paypal(order, "PP-1", "PO-1-paypal", NOW);
        transaction.recordCapture("CAPTURE-1", "COMPLETED", NOW);
        String body = completedCaptureEvent("WH-2", new CaptureResource("CAPTURE-2", "COMPLETED", "USD", "25.50"));
        when(payPal.verifyWebhook(headers(), body)).thenReturn(true);
        when(transactions.findByProviderAndProviderOrderId(PaymentMethod.PAYPAL, "PP-1"))
                .thenReturn(Optional.of(transaction));
        when(orders.findByOrderNoForUpdate("PO-1")).thenReturn(Optional.of(order));
        when(events.insertIfAbsent(eq(PaymentMethod.PAYPAL.name()), eq("WH-2"),
                eq("PAYMENT.CAPTURE.COMPLETED"), any(), any(), any())).thenReturn(1);

        assertThatThrownBy(() -> service.handle(headers(), body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CONFIRMED);
        assertThat(transaction.getProviderCaptureId()).isEqualTo("CAPTURE-1");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void expiresRatherThanCreditsACompletedSignedCaptureForAnExpiredOrder() {
        Instant expiredAt = Instant.now().minusSeconds(1);
        PaymentOrder order = PaymentOrder.paypal("PO-1", 7L, 2_550L, 12_750_000L,
                expiredAt.minusSeconds(1_800), expiredAt);
        PaymentTransaction transaction = PaymentTransaction.paypal(order, "PP-1", "PO-1-paypal", NOW);
        String body = completedCaptureEvent("WH-1");
        when(payPal.verifyWebhook(headers(), body)).thenReturn(true);
        when(transactions.findByProviderAndProviderOrderId(PaymentMethod.PAYPAL, "PP-1"))
                .thenReturn(Optional.of(transaction));
        when(orders.findByOrderNoForUpdate("PO-1")).thenReturn(Optional.of(order));
        when(events.insertIfAbsent(eq(PaymentMethod.PAYPAL.name()), eq("WH-1"),
                eq("PAYMENT.CAPTURE.COMPLETED"), any(), any(), any())).thenReturn(1);

        service.handle(headers(), body);

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.EXPIRED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void handlesWebhookInsideATransactionSoTheCreditListenerRunsAfterCommit() throws Exception {
        Method handle = PayPalWebhookService.class.getMethod("handle", Map.class, String.class);

        assertThat(handle.getAnnotation(Transactional.class)).isNotNull();
    }

    private static PaymentOrder waitingOrder() {
        return PaymentOrder.paypal("PO-1", 7L, 2_550L, 12_750_000L, NOW, NOW.plusSeconds(1_800));
    }

    private static Map<String, String> headers() {
        return Map.of("paypal-transmission-id", "transmission-1");
    }

    private static String completedCaptureEvent(String eventId) {
        return completedCaptureEvent(eventId, new CaptureResource("CAPTURE-1", "COMPLETED", "USD", "25.50"));
    }

    private static String completedCaptureEvent(String eventId, CaptureResource capture) {
        return """
                {"id":"%s","event_type":"PAYMENT.CAPTURE.COMPLETED","resource":{
                  "id":"%s","status":"%s","amount":{"currency_code":"%s","value":"%s"},
                  "supplementary_data":{"related_ids":{"order_id":"PP-1"}}
                }}
                """.formatted(eventId, capture.captureId(), capture.status(), capture.currency(), capture.amount());
    }

    private record CaptureResource(String captureId, String status, String currency, String amount) {
    }
}
