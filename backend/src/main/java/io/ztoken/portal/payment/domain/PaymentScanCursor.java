package io.ztoken.portal.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 每个收款地址的 TronGrid 翻页游标，避免高流量地址遗漏旧页交易。 */
@Entity
@Table(name = "payment_scan_cursors")
public class PaymentScanCursor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 32) private String provider;
    @ManyToOne @JoinColumn(name = "payment_address_id", nullable = false) private PaymentAddress paymentAddress;
    @Column(length = 512) private String fingerprint;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected PaymentScanCursor() { }
    public PaymentScanCursor(String provider, PaymentAddress paymentAddress, Instant now) { this.provider = provider; this.paymentAddress = paymentAddress; this.updatedAt = now; }
    public String getFingerprint() { return fingerprint; }
    public void advance(String nextFingerprint, Instant now) { fingerprint = nextFingerprint; updatedAt = now; }
}
