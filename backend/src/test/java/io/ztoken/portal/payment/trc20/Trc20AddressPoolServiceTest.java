package io.ztoken.portal.payment.trc20;

import io.ztoken.portal.payment.domain.PaymentAddress;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.repository.PaymentAddressRepository;
import io.ztoken.portal.payment.repository.PaymentAmountRegistryRepository;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Trc20AddressPoolServiceTest {

    @Mock private PaymentAddressRepository addresses;
    @Mock private PaymentAmountRegistryRepository amounts;
    @Mock private PaymentOrderRepository orders;
    @Captor private ArgumentCaptor<PaymentOrder> orderCaptor;

    @Test
    void assignsTheLeastLoadedAddressAndFirstAvailableSuffix() {
        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        PaymentAddress leastLoaded = new PaymentAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", now);
        PaymentAddress busy = new PaymentAddress("TLa2f6VPqDgRE67v1736s7bJ8Ray5wYjU7", now);
        busy.incrementActiveOrderCount();
        when(addresses.lockEnabledOrderedByLoad()).thenReturn(List.of(leastLoaded, busy));
        when(amounts.existsByPaymentAddressAndPayableMinor(leastLoaded, 25_000_001L)).thenReturn(false);
        when(orders.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentOrder order = new Trc20AddressPoolService(addresses, amounts, orders, true)
                .createOrder(7L, 2_500L, 12_500_000L, now, now.plusSeconds(1_800));

        verify(orders).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue()).isSameAs(order);
        assertThat(order.getReceiveAddress()).isEqualTo(leastLoaded.getAddress());
        assertThat(order.getPayableMinor()).isEqualTo(25_000_001L);
        assertThat(leastLoaded.getActiveOrderCount()).isEqualTo(1);
    }
}
