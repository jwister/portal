package io.ztoken.portal.payment.api;

import io.ztoken.portal.payment.domain.PaymentAddress;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.repository.PaymentAddressRepository;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.payment.repository.PaymentScanCursorRepository;
import io.ztoken.portal.session.NewApiIdentity;
import io.ztoken.portal.session.PortalSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Trc20PaymentControllerTest {
    private static final AtomicLong USERS = new AtomicLong(40_000L);
    @Autowired private TestRestTemplate http;
    @Autowired private PortalSessionService sessions;
    @Autowired private PaymentOrderRepository orders;
    @Autowired private PaymentAddressRepository addresses;
    @Autowired private PaymentScanCursorRepository scanCursors;

    @AfterEach
    void cleanup() { orders.deleteAll(); scanCursors.deleteAll(); addresses.deleteAll(); }

    @Test
    void returnsTrc20InstructionOnlyForTheOrderOwner() {
        long userId = USERS.incrementAndGet();
        PaymentOrder order = savedOrder(userId);

        ResponseEntity<Map> response = http.exchange("/api/payments/orders/" + order.getOrderNo() + "/trc20/status", HttpMethod.GET,
                authed(null, sessionFor(userId)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("receiveAddress", "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE")
                .containsEntry("payableAmount", "1.000001").containsEntry("payableCurrency", "USDT");
    }

    @Test
    void doesNotExposeTrc20InstructionToAnotherPortalUser() {
        PaymentOrder order = savedOrder(USERS.incrementAndGet());

        ResponseEntity<Map> response = http.exchange("/api/payments/orders/" + order.getOrderNo() + "/trc20/status", HttpMethod.GET,
                authed(null, sessionFor(USERS.incrementAndGet())), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private PaymentOrder savedOrder(long userId) {
        Instant now = Instant.now();
        PaymentAddress address = addresses.findByAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE")
                .orElseGet(() -> addresses.save(new PaymentAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", now)));
        return orders.save(PaymentOrder.usdtTrc20("PO_TRON_" + userId, userId, 100L, 500_000L, address, 1_000_001L, now, now.plusSeconds(1_800)));
    }
    private String sessionFor(long userId) { return sessions.create(new NewApiIdentity(userId, "user" + userId), "access").getId(); }
    private HttpEntity<?> authed(Object body, String session) { HttpHeaders headers = new HttpHeaders(); headers.add(HttpHeaders.COOKIE, "PORTAL_SESSION=" + session); return new HttpEntity<>(body, headers); }
}
