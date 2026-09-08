package io.ztoken.portal.payment.provider;

import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.trc20.Trc20AddressPoolService;
import io.ztoken.portal.payment.trc20.Trc20PaymentScanner;
import io.ztoken.portal.payment.trc20.TxidVerificationService;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** TRC20 地址池、扫码与即时 TxID 核验的渠道声明。 */
@Component
public class Trc20PaymentProvider implements PaymentProvider {
    private final Trc20AddressPoolService addressPool;
    @SuppressWarnings("unused") private final Trc20PaymentScanner scanner;
    @SuppressWarnings("unused") private final TxidVerificationService txids;
    public Trc20PaymentProvider(Trc20AddressPoolService addressPool, Trc20PaymentScanner scanner, TxidVerificationService txids) {
        this.addressPool = addressPool; this.scanner = scanner; this.txids = txids;
    }
    @Override public PaymentMethod method() { return PaymentMethod.USDT_TRC20; }

    @Override
    public PaymentOrder createOrder(long userId, long amountUsdMinor, long quotaToCredit, Instant createdAt, Instant expiresAt) {
        return addressPool.createOrder(userId, amountUsdMinor, quotaToCredit, createdAt, expiresAt);
    }
}
