package io.ztoken.portal.payment.trc20;

import io.ztoken.portal.payment.domain.PaymentAddress;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.repository.PaymentAddressRepository;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.payment.repository.PaymentScanCursorRepository;
import io.ztoken.portal.payment.domain.PaymentScanCursor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/** 独立于客户操作扫描地址池：找到精确地址和金额的待付款订单后，仍交严格核验服务确认。 */
@Service
public class Trc20PaymentScanner {
    private final PaymentAddressRepository addresses;
    private final PaymentOrderRepository orders;
    private final TronGridTransferClient client;
    private final TransferVerificationService verifier;
    private final PaymentScanCursorRepository cursors;

    public Trc20PaymentScanner(PaymentAddressRepository addresses, PaymentOrderRepository orders,
                               TronGridTransferClient client, TransferVerificationService verifier, PaymentScanCursorRepository cursors) {
        this.addresses = Objects.requireNonNull(addresses, "addresses");
        this.orders = Objects.requireNonNull(orders, "orders");
        this.client = Objects.requireNonNull(client, "client");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.cursors = Objects.requireNonNull(cursors, "cursors");
    }

    @Scheduled(fixedDelayString = "${payment.trc20.scan-fixed-delay-ms:5000}")
    @Transactional
    public void scanAddressPool() {
        Instant now = Instant.now();
        for (PaymentAddress address : addresses.findByEnabledTrue()) {
            PaymentScanCursor cursor = cursors.findByProviderAndPaymentAddressId("TRONGRID", address.getId())
                    .orElseGet(() -> cursors.save(new PaymentScanCursor("TRONGRID", address, now)));
            TransferQueryResult query = client.findByReceiveAddress(address.getAddress(), cursor.getFingerprint());
            if (!query.succeeded()) continue;
            for (ObservedTransfer transfer : query.transfers()) {
                orders.findWaitingByReceiveAddressAndPayableMinor(address.getAddress(), transfer.amountMinor())
                        .flatMap(order -> orders.findByOrderNoForUpdate(order.getOrderNo()))
                        .ifPresent(order -> verifier.verify(order, transfer, now));
            }
            cursor.advance(query.nextFingerprint(), now);
        }
    }
}
