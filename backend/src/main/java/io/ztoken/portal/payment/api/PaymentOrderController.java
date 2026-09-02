package io.ztoken.portal.payment.api;

import io.ztoken.portal.payment.order.PaymentOrderService;
import io.ztoken.portal.payment.order.PaymentOrderView;
import io.ztoken.portal.session.PortalPrincipal;
import io.ztoken.portal.session.PortalSessionService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/payments/orders")
public class PaymentOrderController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentOrderService orders;
    private final PortalSessionService sessions;

    public PaymentOrderController(PaymentOrderService orders, PortalSessionService sessions) {
        this.orders = orders;
        this.sessions = sessions;
    }

    @PostMapping
    public ResponseEntity<PaymentOrderResponse> create(
            @CookieValue(value = "PORTAL_SESSION", required = false) String sessionId,
            @Valid @RequestBody CreatePaymentOrderRequest request) {
        PortalPrincipal principal = sessions.require(sessionId);
        try {
            PaymentOrderView order = orders.createForUser(principal, new BigDecimal(request.amount()));
            return noStore(ResponseEntity.status(201).body(PaymentOrderResponse.from(order)));
        } catch (IllegalArgumentException exception) {
            throw PaymentApiException.invalidRequest(exception);
        } catch (IllegalStateException exception) {
            throw PaymentApiException.serviceUnavailable(exception);
        }
    }

    @GetMapping
    public ResponseEntity<PaymentOrderListResponse> list(
            @CookieValue(value = "PORTAL_SESSION", required = false) String sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PortalPrincipal principal = sessions.require(sessionId);
        if (page < 1 || pageSize < 1) {
            throw PaymentApiException.invalidRequest(null);
        }

        int boundedPageSize = Math.min(pageSize, MAX_PAGE_SIZE);
        List<PaymentOrderResponse> allOrders = orders.listForUser(principal).stream()
                .map(PaymentOrderResponse::from)
                .toList();
        long total = allOrders.size();
        long start = ((long) page - 1L) * boundedPageSize;
        List<PaymentOrderResponse> items = start >= total ? List.of()
                : allOrders.subList((int) start, (int) Math.min(total, start + boundedPageSize));

        return noStore(ResponseEntity.ok(new PaymentOrderListResponse(items, page, boundedPageSize, total)));
    }

    @GetMapping("/{orderNo}")
    public ResponseEntity<PaymentOrderResponse> detail(
            @CookieValue(value = "PORTAL_SESSION", required = false) String sessionId,
            @PathVariable String orderNo) {
        PortalPrincipal principal = sessions.require(sessionId);
        return noStore(ResponseEntity.ok(PaymentOrderResponse.from(requireOwnedOrder(principal, orderNo))));
    }

    private PaymentOrderView requireOwnedOrder(PortalPrincipal principal, String orderNo) {
        return orders.findForUser(principal, orderNo).orElseThrow(PaymentApiException::orderNotFound);
    }

    private static <T> ResponseEntity<T> noStore(ResponseEntity<T> response) {
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                .cacheControl(CacheControl.noStore()).body(response.getBody());
    }
}
