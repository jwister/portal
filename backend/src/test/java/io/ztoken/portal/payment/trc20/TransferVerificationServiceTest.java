package io.ztoken.portal.payment.trc20;

import io.ztoken.portal.payment.config.PaymentProperties;
import io.ztoken.portal.payment.domain.PaymentAddress;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentOrderStatus;
import io.ztoken.portal.payment.repository.ChainTransferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransferVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");

    @Mock private ChainTransferRepository transfers;

    @Test
    void confirmsOnlyExactUsdtTransferWithTwentyConfirmations() {
        PaymentOrder order = order();
        PaymentProperties properties = new PaymentProperties();
        ObservedTransfer transfer = new ObservedTransfer("tx-1", 0L, properties.getTrc20().getUsdtContract(), "TFROM",
                order.getReceiveAddress(), order.getPayableMinor(), 1L, NOW.plusSeconds(1), true, 20);

        VerificationResult result = new TransferVerificationService(properties, transfers).verify(order, transfer, NOW.plusSeconds(2));

        assertThat(result).isEqualTo(VerificationResult.CONFIRMED);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CONFIRMED);
        verify(transfers).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void keepsOrderWaitingWhenTransferHasNineteenConfirmations() {
        PaymentOrder order = order();
        PaymentProperties properties = new PaymentProperties();
        ObservedTransfer transfer = new ObservedTransfer("tx-2", 0L, properties.getTrc20().getUsdtContract(), "TFROM",
                order.getReceiveAddress(), order.getPayableMinor(), 1L, NOW.plusSeconds(1), true, 19);

        assertThat(new TransferVerificationService(properties, transfers).verify(order, transfer, NOW.plusSeconds(2)))
                .isEqualTo(VerificationResult.PENDING_CONFIRMATION);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.WAITING_PAYMENT);
        verify(transfers, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private PaymentOrder order() {
        PaymentAddress address = new PaymentAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", NOW);
        return PaymentOrder.usdtTrc20("PO-TRON", 7L, 100L, 500_000L, address, 1_000_001L, NOW, NOW.plusSeconds(1_800));
    }
}
