package io.ztoken.portal.payment.api;

import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.order.PaymentOrderService;
import io.ztoken.portal.payment.order.PaymentOrderView;
import io.ztoken.portal.payment.paypal.PayPalOrderConflictException;
import io.ztoken.portal.payment.paypal.PayPalPaymentService;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.session.NewApiIdentity;
import io.ztoken.portal.session.PortalPrincipal;
import io.ztoken.portal.session.PortalSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Execution(ExecutionMode.SAME_THREAD)
class PayPalPaymentControllerTest {

    private static final AtomicLong USER_IDS = new AtomicLong(20_000L);

    @MockBean
    private PayPalPaymentService paypalPayments;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private PortalSessionService sessions;

    @Autowired
    private PaymentOrderService orders;

    @Autowired
    private PaymentOrderRepository paymentOrders;

    @DynamicPropertySource
    static void paypalProperties(DynamicPropertyRegistry registry) {
        registry.add("payment.paypal.client-id", () -> "public-client-id");
        registry.add("payment.paypal.client-secret", () -> "server-secret");
        registry.add("payment.paypal.webhook-id", () -> "server-webhook-id");
        registry.add("payment.paypal.mode", () -> "sandbox");
    }

    @BeforeEach
    void resetPayPalPaymentService() {
        Mockito.reset(paypalPayments);
    }

    @AfterEach
    void removePaymentOrdersCreatedByThisIntegrationTest() {
        paymentOrders.deleteAll();
    }

    @Test
    void returnsOnlyPublicPayPalConfigForTheCurrentOrderOwner() {
        long userId = nextUserId();
        String sessionId = sessionFor(userId);
        PaymentOrderView order = createFor(userId);

        ResponseEntity<Map> response = http.exchange("/api/payments/orders/" + order.orderNo() + "/paypal/config",
                HttpMethod.GET, authed(null, sessionId), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getBody()).containsOnly(
                Map.entry("clientId", "public-client-id"),
                Map.entry("mode", "sandbox"));
    }

    @Test
    void customerCannotCreateAProviderOrderForAnotherUsersOrder() {
        String sessionForUserA = sessionFor(nextUserId());
        PaymentOrderView orderForUserB = createFor(nextUserId());

        ResponseEntity<Map> response = http.exchange("/api/payments/orders/" + orderForUserB.orderNo() + "/paypal/order",
                HttpMethod.POST, authed(Map.of("amount", "0.01", "providerOrderId", "attacker"), sessionForUserA), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", "PAYMENT_ORDER_NOT_FOUND");
        verifyNoInteractions(paypalPayments);
    }

    @Test
    void createsTheProviderOrderFromTheLocalOrderWithoutTrustingRequestFields() {
        long userId = nextUserId();
        String sessionId = sessionFor(userId);
        PaymentOrderView order = createFor(userId);
        when(paypalPayments.createProviderOrder(order.orderNo())).thenReturn("PP-local-order");

        ResponseEntity<Map> response = http.exchange("/api/payments/orders/" + order.orderNo() + "/paypal/order",
                HttpMethod.POST, authed(Map.of("amount", "0.01", "providerOrderId", "attacker", "captureId", "attacker"),
                        sessionId), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getBody()).containsOnly(Map.entry("providerOrderId", "PP-local-order"));
        verify(paypalPayments).createProviderOrder(eq(order.orderNo()));
    }

    @Test
    void capturesFromTheLocalProviderTransactionWithoutAcceptingACaptureId() {
        long userId = nextUserId();
        String sessionId = sessionFor(userId);
        PaymentOrderView localOrder = createFor(userId);
        PaymentOrder captured = PaymentOrder.paypal(localOrder.orderNo(), userId, localOrder.amountUsdMinor(),
                localOrder.quotaToCredit(), Instant.now(), localOrder.expiresAt());
        when(paypalPayments.capture(localOrder.orderNo())).thenReturn(captured);

        ResponseEntity<Map> response = http.exchange("/api/payments/orders/" + localOrder.orderNo() + "/paypal/capture",
                HttpMethod.POST, authed(Map.of("captureId", "attacker-capture-id"), sessionId), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getBody()).containsEntry("orderNo", localOrder.orderNo())
                .doesNotContainKeys("captureId", "providerOrderId");
        verify(paypalPayments).capture(eq(localOrder.orderNo()));
    }

    @Test
    void mapsExpiredLocalOrderCreationToASafeConflict() {
        long userId = nextUserId();
        String sessionId = sessionFor(userId);
        PaymentOrderView order = createFor(userId);
        when(paypalPayments.createProviderOrder(order.orderNo()))
                .thenThrow(new PayPalOrderConflictException("Payment order has expired"));

        ResponseEntity<Map> response = http.exchange("/api/payments/orders/" + order.orderNo() + "/paypal/order",
                HttpMethod.POST, authed(null, sessionId), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "PAYMENT_ACTION_CONFLICT");
        assertThat(response.getBody().get("message").toString()).doesNotContain("expired");
    }

    @Test
    void mapsMissingLocalProviderOrderToASafeConflict() {
        long userId = nextUserId();
        String sessionId = sessionFor(userId);
        PaymentOrderView order = createFor(userId);
        when(paypalPayments.capture(order.orderNo()))
                .thenThrow(new PayPalOrderConflictException("PayPal provider order has not been created"));

        ResponseEntity<Map> response = http.exchange("/api/payments/orders/" + order.orderNo() + "/paypal/capture",
                HttpMethod.POST, authed(null, sessionId), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "PAYMENT_ACTION_CONFLICT");
        assertThat(response.getBody().get("message").toString()).doesNotContain("PayPal");
    }

    @Test
    void mapsProviderFailuresToASafeServiceError() {
        long userId = nextUserId();
        String sessionId = sessionFor(userId);
        PaymentOrderView order = createFor(userId);
        when(paypalPayments.createProviderOrder(order.orderNo()))
                .thenThrow(new IllegalStateException("PayPal OAuth failed with client secret server-secret"));

        ResponseEntity<Map> response = http.exchange("/api/payments/orders/" + order.orderNo() + "/paypal/order",
                HttpMethod.POST, authed(null, sessionId), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("code", "PAYMENT_SERVICE_UNAVAILABLE");
        assertThat(response.getBody().get("message").toString()).doesNotContain("PayPal").doesNotContain("server-secret");
    }

    private PaymentOrderView createFor(long userId) {
        return orders.createForUser(new PortalPrincipal(userId, "user" + userId, "access-token"), new BigDecimal("25.50"));
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
