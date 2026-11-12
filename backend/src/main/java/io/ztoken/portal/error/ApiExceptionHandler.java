package io.ztoken.portal.error;

import io.ztoken.portal.newapi.NewApiAuthenticationException;
import io.ztoken.portal.newapi.NewApiException;
import io.ztoken.portal.newapi.NewApiRateLimitException;
import io.ztoken.portal.newapi.NewApiSessionLimitException;
import io.ztoken.portal.newapi.NewApiEmailVerificationException;
import io.ztoken.portal.newapi.NewApiUnsupportedException;
import io.ztoken.portal.payment.api.PaymentApiException;
import io.ztoken.portal.session.UnauthenticatedException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** 保留上游限流语义和安全等待时间，前端据此停止重复提交。 */
    @ExceptionHandler(NewApiRateLimitException.class)
    public ResponseEntity<Map<String, String>> newApiRateLimited(NewApiRateLimitException exception) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).cacheControl(CacheControl.noStore());
        if (exception.getRetryAfterSeconds() != null) response.header("Retry-After", exception.getRetryAfterSeconds().toString());
        return response.body(Map.of("code", "NEWAPI_RATE_LIMITED", "message", "操作频繁，请稍后重试"));
    }

    @ExceptionHandler(PaymentApiException.class)
    public ResponseEntity<Map<String, String>> paymentFailure(PaymentApiException exception) {
        return ResponseEntity.status(exception.getStatus()).cacheControl(CacheControl.noStore())
                .body(Map.of("code", exception.getCode(), "message", exception.getSafeMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalidPaymentRequest() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(Map.of("code", "INVALID_PAYMENT_REQUEST", "message", "Payment request is invalid"));
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<Map<String, String>> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore())
                .body(Map.of("code", "UNAUTHENTICATED", "message", "Authentication is required"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidAuthRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(Map.of("code", "INVALID_AUTH_REQUEST", "message", exception.getMessage()));
    }

    @ExceptionHandler(NewApiAuthenticationException.class)
    public ResponseEntity<Map<String, String>> newApiAuthenticationFailed() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", "NEWAPI_AUTH_FAILED", "message", "Unable to verify sign-in details"));
    }

    @ExceptionHandler(NewApiUnsupportedException.class)
    public ResponseEntity<Map<String, String>> newApiUnsupported() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("code", "NOT_SUPPORTED", "message", "This operation is not supported"));
    }

    @ExceptionHandler(NewApiSessionLimitException.class)
    public ResponseEntity<Map<String, String>> newApiSessionLimit() {
        return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore())
                .body(Map.of("code", "NEWAPI_SESSION_LIMIT", "message", "登录会话数量已达上限，请在已登录的 NewAPI 设备中退出其他会话后重试"));
    }

    @ExceptionHandler(NewApiEmailVerificationException.class)
    public ResponseEntity<Map<String, String>> newApiEmailVerificationFailure() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("code", "EMAIL_VERIFICATION_UNAVAILABLE", "message", "邮箱验证码发送服务暂不可用，请稍后重试"));
    }

    @ExceptionHandler(NewApiException.class)
    public ResponseEntity<Map<String, String>> newApiFailure(NewApiException ex) {
        org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class).error("Upstream request failed", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("code", "NEWAPI_ERROR", "message", "Upstream request failed"));
    }
}
