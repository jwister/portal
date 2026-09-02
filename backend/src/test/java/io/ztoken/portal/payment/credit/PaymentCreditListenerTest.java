package io.ztoken.portal.payment.credit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCreditListenerTest {

    @Mock
    private PaymentCreditService credits;

    private PaymentCreditListener listener;

    @BeforeEach
    void setUp() {
        listener = new PaymentCreditListener(credits);
    }

    @Test
    void creditsTheOrderNamedByTheConfirmedPaymentEvent() {
        listener.onPaymentConfirmed(new PaymentConfirmedEvent("PO_EVENT"));

        verify(credits).creditConfirmedOrder("PO_EVENT");
    }

    @Test
    void listensOnlyAfterTheConfirmingTransactionCommits() throws Exception {
        Method method = PaymentCreditListener.class.getMethod("onPaymentConfirmed", PaymentConfirmedEvent.class);
        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
