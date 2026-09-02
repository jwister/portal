package io.ztoken.portal.payment.credit;

import io.ztoken.portal.payment.config.PaymentProperties;
import io.ztoken.portal.payment.domain.CreditAttempt;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentOrderStatus;
import io.ztoken.portal.payment.repository.CreditAttemptRepository;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCreditServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T10:00:00Z");

    @Mock
    private PaymentOrderRepository orders;

    @Mock
    private CreditAttemptRepository attempts;

    @Mock
    private NewApiCreditClient newApiCredit;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Captor
    private ArgumentCaptor<CreditAttempt> savedAttempt;

    private PaymentProperties properties;
    private PaymentCreditService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        properties = new PaymentProperties();
        service = new PaymentCreditService(orders, attempts, newApiCredit, properties, transactionManager);
    }

    @Test
    void commitsTheCreditingClaimBeforeCallingNewApiThenMarksPaid() {
        PaymentOrder order = confirmedOrder();
        lockReturns(order);
        when(attempts.save(any(CreditAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(newApiCredit.addQuota(7L, 2_500_000L)).thenAnswer(invocation -> {
            assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CREDITING);
            return CreditResult.SUCCESS;
        });

        service.creditConfirmedOrder(order.getOrderNo());

        InOrder sequence = inOrder(transactionManager, orders, attempts, newApiCredit);
        sequence.verify(transactionManager).getTransaction(argThat(definition ->
                definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        sequence.verify(orders).findByOrderNoForUpdate(order.getOrderNo());
        sequence.verify(attempts).save(any(CreditAttempt.class));
        sequence.verify(transactionManager).commit(any());
        sequence.verify(newApiCredit).addQuota(7L, 2_500_000L);
        sequence.verify(transactionManager).getTransaction(argThat(definition ->
                definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        sequence.verify(orders).findByOrderNoForUpdate(order.getOrderNo());
        sequence.verify(attempts).save(any(CreditAttempt.class));
        sequence.verify(transactionManager).commit(any());
        verify(attempts, times(2)).save(savedAttempt.capture());
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(lastSavedAttempt().getPaymentOrder()).isSameAs(order);
        assertThat(lastSavedAttempt().getStatus()).isEqualTo(CreditAttempt.Status.SUCCESS);
        assertThat(lastSavedAttempt().getFinishedAt()).isNotNull();
    }

    @Test
    void recordsExplicitNewApiRejectionAsCreditFailed() {
        PaymentOrder order = confirmedOrder();
        lockReturns(order);
        when(attempts.save(any(CreditAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(newApiCredit.addQuota(7L, 2_500_000L)).thenReturn(CreditResult.FAILED);

        service.creditConfirmedOrder(order.getOrderNo());

        verify(attempts, times(2)).save(savedAttempt.capture());
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CREDIT_FAILED);
        assertThat(lastSavedAttempt().getStatus()).isEqualTo(CreditAttempt.Status.FAILED);
    }

    @Test
    void failsAConfirmedOrderWithoutCallingNewApiAfterTheWalletCapIsReduced() {
        PaymentOrder order = confirmedOrder();
        lockReturns(order);
        when(attempts.save(any(CreditAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        properties.getNewApiCredit().setMaxWalletQuota(1_000_000L);

        service.creditConfirmedOrder(order.getOrderNo());

        verify(newApiCredit, never()).addQuota(any(Long.class), any(Long.class));
        verify(attempts, times(2)).save(savedAttempt.capture());
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CREDIT_FAILED);
        assertThat(lastSavedAttempt().getStatus()).isEqualTo(CreditAttempt.Status.FAILED);
        assertThat(lastSavedAttempt().getMessage())
                .isEqualTo("Payment quota exceeds the current NewAPI wallet limit");
    }

    @Test
    void unknownCreditResultIsNeverRetriedAutomatically() {
        PaymentOrder order = confirmedOrder();
        lockReturns(order);
        when(attempts.save(any(CreditAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(newApiCredit.addQuota(7L, 2_500_000L)).thenReturn(CreditResult.UNKNOWN);

        service.creditConfirmedOrder(order.getOrderNo());
        service.creditConfirmedOrder(order.getOrderNo());

        verify(newApiCredit).addQuota(7L, 2_500_000L);
        verify(attempts, times(2)).save(savedAttempt.capture());
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CREDIT_UNKNOWN);
        assertThat(lastSavedAttempt().getStatus()).isEqualTo(CreditAttempt.Status.UNKNOWN);
    }

    @Test
    void unexpectedClientExceptionBecomesCreditUnknownWithoutRetry() {
        PaymentOrder order = confirmedOrder();
        lockReturns(order);
        when(attempts.save(any(CreditAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(newApiCredit.addQuota(7L, 2_500_000L)).thenThrow(new IllegalStateException("network failure"));

        service.creditConfirmedOrder(order.getOrderNo());

        verify(attempts, times(2)).save(savedAttempt.capture());
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CREDIT_UNKNOWN);
        assertThat(lastSavedAttempt().getStatus()).isEqualTo(CreditAttempt.Status.UNKNOWN);
    }

    @Test
    void persistenceFailureAfterSuccessfulCreditLeavesOrderNonRetryable() {
        PaymentOrder order = confirmedOrder();
        lockReturns(order);
        AtomicInteger saveCount = new AtomicInteger();
        when(attempts.save(any(CreditAttempt.class))).thenAnswer(invocation -> {
            if (saveCount.incrementAndGet() == 1) {
                return invocation.getArgument(0);
            }
            throw new DataAccessResourceFailureException("database unavailable");
        });
        when(newApiCredit.addQuota(7L, 2_500_000L)).thenReturn(CreditResult.SUCCESS);

        assertThatThrownBy(() -> service.creditConfirmedOrder(order.getOrderNo()))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CREDITING);

        service.creditConfirmedOrder(order.getOrderNo());

        verify(newApiCredit).addQuota(7L, 2_500_000L);
        verify(attempts, times(2)).save(any(CreditAttempt.class));
    }

    @Test
    void doesNotInvokeNewApiUnlessTheLockedOrderIsConfirmed() {
        PaymentOrder waitingOrder = PaymentOrder.paypal(
                "PO_WAITING", 7L, 500L, 2_500_000L, NOW, NOW.plusSeconds(60));
        lockReturns(waitingOrder);

        service.creditConfirmedOrder(waitingOrder.getOrderNo());

        verify(newApiCredit, never()).addQuota(any(Long.class), any(Long.class));
        verify(attempts, never()).save(any(CreditAttempt.class));
        assertThat(waitingOrder.getStatus()).isEqualTo(PaymentOrderStatus.WAITING_PAYMENT);
    }

    private PaymentOrder confirmedOrder() {
        PaymentOrder order = PaymentOrder.paypal(
                "PO_CREDIT", 7L, 500L, 2_500_000L, NOW, NOW.plusSeconds(60));
        order.confirm(NOW);
        return order;
    }

    private CreditAttempt lastSavedAttempt() {
        var savedAttempts = savedAttempt.getAllValues();
        return savedAttempts.get(savedAttempts.size() - 1);
    }

    private void lockReturns(PaymentOrder order) {
        when(orders.findByOrderNoForUpdate(eq(order.getOrderNo()))).thenReturn(Optional.of(order));
    }
}
