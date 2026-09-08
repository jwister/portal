package io.ztoken.portal.payment.trc20;

import io.ztoken.portal.payment.domain.PaymentAddress;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.repository.PaymentAddressRepository;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.payment.repository.PaymentScanCursorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class Trc20PaymentScannerTest {
    @Mock private PaymentAddressRepository addresses;
    @Mock private PaymentOrderRepository orders;
    @Mock private TronGridTransferClient client;
    @Mock private TransferVerificationService verifier;
    @Mock private PaymentScanCursorRepository cursors;

    @Test
    void verifiesObservedAddressPoolTransfersAgainstTheirExactWaitingOrder() {
        Instant now = Instant.now();
        PaymentAddress address = spy(new PaymentAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", now));
        doReturn(1L).when(address).getId();
        PaymentOrder order = PaymentOrder.usdtTrc20("PO_TRON_1", 7L, 100L, 500_000L, address, 1_000_001L, now, now.plusSeconds(1_800));
        ObservedTransfer transfer = new ObservedTransfer("tx-1", 0L, "contract", "from", address.getAddress(), 1_000_001L, 1L, now.plusSeconds(1), true, 20);
        when(addresses.findByEnabledTrue()).thenReturn(List.of(address));
        when(cursors.findByProviderAndPaymentAddressId("TRONGRID", address.getId())).thenReturn(Optional.empty());
        when(cursors.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.findByReceiveAddress(address.getAddress(), null)).thenReturn(TransferQueryResult.success(List.of(transfer), "next-page"));
        when(orders.findWaitingByReceiveAddressAndPayableMinor(address.getAddress(), 1_000_001L)).thenReturn(Optional.of(order));
        when(orders.findByOrderNoForUpdate(order.getOrderNo())).thenReturn(Optional.of(order));

        new Trc20PaymentScanner(addresses, orders, client, verifier, cursors).scanAddressPool();

        verify(verifier).verify(eq(order), eq(transfer), org.mockito.ArgumentMatchers.any(Instant.class));
    }
}
