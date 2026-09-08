package io.ztoken.portal.payment.api;

import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.session.PortalPrincipal;
import io.ztoken.portal.session.PortalSessionService;
import io.ztoken.portal.payment.trc20.TxidVerificationService;
import io.ztoken.portal.payment.trc20.VerificationResult;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 为当前 Portal 会话安全返回 TRC20 支付指引。 */
@RestController
@RequestMapping("/api/payments/orders")
public class Trc20PaymentController {
    private final PaymentOrderRepository orders;
    private final PortalSessionService sessions;
    private final TxidVerificationService txids;
    public Trc20PaymentController(PaymentOrderRepository orders, PortalSessionService sessions, TxidVerificationService txids) { this.orders = orders; this.sessions = sessions; this.txids = txids; }

    @GetMapping("/{orderNo}/trc20/status")
    public ResponseEntity<Trc20PaymentInstructionResponse> status(@CookieValue(value = "PORTAL_SESSION", required = false) String sessionId,
                                                                    @PathVariable String orderNo) {
        PortalPrincipal principal = sessions.require(sessionId);
        PaymentOrder order = orders.findByOrderNo(orderNo)
                .filter(item -> item.getNewApiUserId() == principal.userId() && item.getPaymentMethod() == PaymentMethod.USDT_TRC20)
                .orElseThrow(PaymentApiException::orderNotFound);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Trc20PaymentInstructionResponse.from(order));
    }

    @PostMapping("/{orderNo}/trc20/txid")
    public ResponseEntity<TxidVerificationResponse> submitTxid(@CookieValue(value = "PORTAL_SESSION", required = false) String sessionId,
                                                                 @PathVariable String orderNo, @Valid @RequestBody SubmitTrc20TxidRequest request) {
        PortalPrincipal principal = sessions.require(sessionId);
        try {
            VerificationResult result = txids.submit(principal, orderNo, request.txid());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new TxidVerificationResponse(result));
        } catch (java.util.NoSuchElementException exception) {
            throw PaymentApiException.orderNotFound();
        } catch (IllegalArgumentException exception) {
            throw PaymentApiException.invalidRequest(exception);
        }
    }
}
