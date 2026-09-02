package io.ztoken.portal.payment.api;

import io.ztoken.portal.payment.order.PaymentOrderService;
import io.ztoken.portal.payment.order.PaymentOrderView;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.session.NewApiIdentity;
import io.ztoken.portal.session.PortalSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Execution(ExecutionMode.SAME_THREAD)
class PaymentOrderControllerTest {

    private static final AtomicLong USER_IDS = new AtomicLong(10_000L);

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private PortalSessionService sessions;

    @Autowired
    private PaymentOrderService orders;

    @Autowired
    private PaymentOrderRepository paymentOrders;

    @AfterEach
    void removePaymentOrdersCreatedByThisIntegrationTest() {
        paymentOrders.deleteAll();
    }

    @Test
    void createsAnOrderForTheSessionUserAndPreventsCaching() {
        String sessionId = sessionFor(nextUserId());

        ResponseEntity<Map> response = http.exchange("/api/payments/orders", HttpMethod.POST,
                authed(Map.of("amount", "25.50", "method", "PAYPAL"), sessionId), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getBody()).containsEntry("amountUsdMinor", 2_550)
                .containsEntry("quotaToCredit", 12_750_000)
                .containsEntry("method", "PAYPAL")
                .containsEntry("status", "WAITING_PAYMENT");
        assertThat(response.getBody().get("orderNo").toString()).startsWith("PO_");
    }

    @Test
    void customerCannotReadAnotherUsersOrder() {
        String sessionForUserA = sessionFor(nextUserId());
        PaymentOrderView orderForUserB = orders.createForUser(
                new io.ztoken.portal.session.PortalPrincipal(nextUserId(), "bob", "access-token"),
                new BigDecimal("25.50"));

        ResponseEntity<Map> response = http.exchange("/api/payments/orders/" + orderForUserB.orderNo(),
                HttpMethod.GET, authed(null, sessionForUserA), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", "PAYMENT_ORDER_NOT_FOUND");
    }

    @Test
    void returnsAPaginatedCurrentUserOrderListWithoutCaching() {
        long userId = nextUserId();
        String sessionId = sessionFor(userId);
        List<PaymentOrderView> created = List.of(
                orders.createForUser(new io.ztoken.portal.session.PortalPrincipal(userId, "alice", "access-token"),
                        new BigDecimal("10.00")),
                orders.createForUser(new io.ztoken.portal.session.PortalPrincipal(userId, "alice", "access-token"),
                        new BigDecimal("20.00")),
                orders.createForUser(new io.ztoken.portal.session.PortalPrincipal(userId, "alice", "access-token"),
                        new BigDecimal("30.00")));

        ResponseEntity<Map> response = http.exchange("/api/payments/orders?page=2&pageSize=1", HttpMethod.GET,
                authed(null, sessionId), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getBody()).containsEntry("page", 2).containsEntry("pageSize", 1).containsEntry("total", 3);
        assertThat((List<Map<String, Object>>) response.getBody().get("items")).hasSize(1);
        assertThat(((List<Map<String, Object>>) response.getBody().get("items")).get(0).get("orderNo"))
                .isIn(created.stream().map(PaymentOrderView::orderNo).toList());
    }

    @Test
    void mapsInvalidPaymentAmountToASafeBusinessError() {
        String sessionId = sessionFor(nextUserId());

        ResponseEntity<Map> response = http.exchange("/api/payments/orders", HttpMethod.POST,
                authed(Map.of("amount", "1.001", "method", "PAYPAL"), sessionId), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "INVALID_PAYMENT_REQUEST");
        assertThat(response.getBody().get("message").toString()).doesNotContain("Payment amount");
    }

    @Test
    void rejectsMissingSessionWithoutCachingThePaymentError() {
        ResponseEntity<Map> response = http.getForEntity("/api/payments/orders", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getBody()).containsEntry("code", "UNAUTHENTICATED");
    }

    private String sessionFor(long userId) {
        return sessions.create(new NewApiIdentity(userId, "user" + userId), "access-token").getId();
    }

    private HttpEntity<?> authed(Object body, String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "PORTAL_SESSION=" + sessionId);
        return new HttpEntity<>(body, headers);
    }

    private long nextUserId() {
        return USER_IDS.incrementAndGet();
    }
}
