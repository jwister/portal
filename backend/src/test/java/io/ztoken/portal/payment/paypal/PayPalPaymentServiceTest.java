package io.ztoken.portal.payment.paypal;

import io.ztoken.portal.payment.credit.PaymentConfirmedEvent;
import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentOrderStatus;
import io.ztoken.portal.payment.domain.PaymentTransaction;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayPalPaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T10:00:00Z");

    @Mock
    private PaymentOrderRepository orders;

    @Mock
    private PaymentTransactionRepository transactions;

    @Mock
    private PayPalClient payPal;

    @Mock
    private ApplicationEventPublisher events;

    @Captor
    private ArgumentCaptor<PaymentTransaction> savedTransaction;

    private PayPalPaymentService service;

    @BeforeEach
    void setUp() {
        service = new PayPalPaymentService(orders, transactions, payPal, events);
    }

    @Test
    void createsOneProviderOrderFromLockedLocalCentsAndReusesIt() {
        PaymentOrder order = waitingOrder();
        when(orders.findByOrderNoForUpdate("PO-1")).thenReturn(Optional.of(order));
        when(transactions.findByPaymentOrderAndProvider(order, PaymentMethod.PAYPAL)).thenReturn(Optional.empty());
        when(payPal.createOrder("PO-1", 2_550L, "PO-1-paypal"))
                .thenReturn(new PayPalOrderDetails("PP-1", "CREATED", "USD", 2_550L));
        when(transactions.save(any(PaymentTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.createProviderOrder("PO-1")).isEqualTo("PP-1");

        verify(payPal).createOrder("PO-1", 2_550L, "PO-1-paypal");
        verify(transactions).save(savedTransaction.capture());
        assertThat(savedTransaction.getValue().getProviderOrderId()).isEqualTo("PP-1");
        assertThat(savedTransaction.getValue().getIdempotencyKey()).isEqualTo("PO-1-paypal");
        assertThat(savedTransaction.getValue().getProviderStatus()).isEqualTo("CREATED");

        PaymentTransaction existing = savedTransaction.getValue();
        when(transactions.findByPaymentOrderAndProvider(order, PaymentMethod.PAYPAL)).thenReturn(Optional.of(existing));
        assertThat(service.createProviderOrder("PO-1")).isEqualTo("PP-1");
        verify(payPal).createOrder("PO-1", 2_550L, "PO-1-paypal");
    }

    @Test
    void capturesOnlyTheMatchingCompletedUsdCaptureAndPublishesExistingEvent() {
        PaymentOrder order = waitingOrder();
        PaymentTransaction transaction = PaymentTransaction.paypal(order, "PP-1", "PO-1-paypal", NOW);
        when(orders.findByOrderNoForUpdate("PO-1")).thenReturn(Optional.of(order));
        when(transactions.findByPaymentOrderAndProvider(order, PaymentMethod.PAYPAL)).thenReturn(Optional.of(transaction));
        when(payPal.captureOrder("PP-1", "PO-1-capture"))
                .thenReturn(new PayPalCaptureDetails("PP-1", "CAPTURE-1", "COMPLETED", "USD", 2_550L));

        PaymentOrder captured = service.capture("PO-1");

        assertThat(captured).isSameAs(order);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CONFIRMED);
        assertThat(transaction.getProviderCaptureId()).isEqualTo("CAPTURE-1");
        assertThat(transaction.getProviderStatus()).isEqualTo("COMPLETED");
        verify(orders).save(order);
        verify(transactions).save(transaction);
        verify(events).publishEvent(new PaymentConfirmedEvent("PO-1"));
    }

    @ParameterizedTest
    @MethodSource("invalidCaptures")
    void refusesToConfirmCaptureUnlessOrderCaptureStatusCurrencyAndExactCentsAllMatch(PayPalCaptureDetails details) {
        PaymentOrder order = waitingOrder();
        PaymentTransaction transaction = PaymentTransaction.paypal(order, "PP-1", "PO-1-paypal", NOW);
        when(orders.findByOrderNoForUpdate("PO-1")).thenReturn(Optional.of(order));
        when(transactions.findByPaymentOrderAndProvider(order, PaymentMethod.PAYPAL)).thenReturn(Optional.of(transaction));
        when(payPal.captureOrder("PP-1", "PO-1-capture")).thenReturn(details);

        assertThatThrownBy(() -> service.capture("PO-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.WAITING_PAYMENT);
        assertThat(transaction.getProviderCaptureId()).isNull();
        verify(events, never()).publishEvent(any());
    }

    private static Stream<PayPalCaptureDetails> invalidCaptures() {
        return Stream.of(
                new PayPalCaptureDetails("OTHER-ORDER", "CAPTURE-1", "COMPLETED", "USD", 2_550L),
                new PayPalCaptureDetails("PP-1", "", "COMPLETED", "USD", 2_550L),
                new PayPalCaptureDetails("PP-1", "CAPTURE-1", "PENDING", "USD", 2_550L),
                new PayPalCaptureDetails("PP-1", "CAPTURE-1", "COMPLETED", "EUR", 2_550L),
                new PayPalCaptureDetails("PP-1", "CAPTURE-1", "COMPLETED", "USD", 2_549L)
        );
    }

    @Test
    void marksAnExpiredOrderBeforeSignalingTheCallerAndCommitsThatTransition() {
        Instant expiredAt = Instant.now().minusSeconds(1);
        PaymentOrder order = PaymentOrder.paypal("PO-1", 7L, 2_550L, 12_750_000L,
                expiredAt.minusSeconds(1_800), expiredAt);
        when(orders.findByOrderNoForUpdate("PO-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.createProviderOrder("PO-1"))
                .isInstanceOf(PaymentOrderExpiredException.class);

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.EXPIRED);
        verify(orders).save(order);
    }

    @Test
    void repeatedCaptureOfAnAlreadyConfirmedOrderNeverCallsPaypalOrPublishesAnotherEvent() {
        PaymentOrder order = waitingOrder();
        order.confirm(NOW);
        when(orders.findByOrderNoForUpdate("PO-1")).thenReturn(Optional.of(order));

        assertThat(service.capture("PO-1")).isSameAs(order);

        verify(payPal, never()).captureOrder(any(), any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void createsAndCapturesWithinTransactionsSoTheCreditEventIsDeliveredAfterCommit() throws Exception {
        Method create = PayPalPaymentService.class.getMethod("createProviderOrder", String.class);
        Method capture = PayPalPaymentService.class.getMethod("capture", String.class);

        assertThat(create.getAnnotation(Transactional.class)).isNotNull();
        assertThat(capture.getAnnotation(Transactional.class)).isNotNull();
        assertThat(create.getAnnotation(Transactional.class).noRollbackFor())
                .contains(PaymentOrderExpiredException.class);
        assertThat(capture.getAnnotation(Transactional.class).noRollbackFor())
                .contains(PaymentOrderExpiredException.class);
    }

    private static PaymentOrder waitingOrder() {
        return PaymentOrder.paypal("PO-1", 7L, 2_550L, 12_750_000L, NOW, NOW.plusSeconds(1_800));
    }
}
