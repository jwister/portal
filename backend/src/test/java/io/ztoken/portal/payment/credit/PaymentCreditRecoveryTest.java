package io.ztoken.portal.payment.credit;

import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentOrderStatus;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCreditRecoveryTest {

    @Mock
    private PaymentOrderRepository orders;

    @Mock
    private PaymentCreditService credits;

    private PaymentCreditRecovery recovery;

    @BeforeEach
    void setUp() {
        recovery = new PaymentCreditRecovery(orders, credits);
    }

    @Test
    void startsExistingCreditClaimOnlyForConfirmedOrdersAtApplicationStartup() throws Exception {
        PaymentOrder confirmed = order("PO-CONFIRMED");
        confirmed.confirm(Instant.now());
        PaymentOrder crediting = order("PO-CREDITING");
        crediting.confirm(Instant.now());
        crediting.startCrediting(Instant.now());
        PaymentOrder unknown = order("PO-UNKNOWN");
        unknown.confirm(Instant.now());
        unknown.startCrediting(Instant.now());
        unknown.markCreditUnknown(Instant.now());
        when(orders.findByStatus(PaymentOrderStatus.CONFIRMED)).thenReturn(List.of(confirmed, crediting, unknown));

        recovery.run(new DefaultApplicationArguments());

        verify(orders).findByStatus(PaymentOrderStatus.CONFIRMED);
        verify(credits).creditConfirmedOrder("PO-CONFIRMED");
        verifyNoMoreInteractions(credits);
        assertThat(crediting.getStatus()).isEqualTo(PaymentOrderStatus.CREDITING);
        assertThat(unknown.getStatus()).isEqualTo(PaymentOrderStatus.CREDIT_UNKNOWN);
    }

    @Test
    void continuesScanningConfirmedOrdersWhenOneCreditClaimThrows() throws Exception {
        PaymentOrder first = order("PO-FIRST");
        first.confirm(Instant.now());
        PaymentOrder second = order("PO-SECOND");
        second.confirm(Instant.now());
        when(orders.findByStatus(PaymentOrderStatus.CONFIRMED)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("temporary database failure"))
                .when(credits).creditConfirmedOrder("PO-FIRST");

        recovery.run(new DefaultApplicationArguments());

        verify(credits).creditConfirmedOrder("PO-FIRST");
        verify(credits).creditConfirmedOrder("PO-SECOND");
    }

    private static PaymentOrder order(String orderNo) {
        Instant now = Instant.now();
        return PaymentOrder.paypal(orderNo, 7L, 500L, 2_500_000L, now, now.plusSeconds(1_800));
    }
}
