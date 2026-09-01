package io.ztoken.portal.error;

import io.ztoken.portal.session.UnauthenticatedException;
import io.ztoken.portal.newapi.NewApiAuthenticationException;
import io.ztoken.portal.newapi.NewApiException;
import io.ztoken.portal.newapi.NewApiUnsupportedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<Map<String, String>> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", "UNAUTHENTICATED", "message", "Authentication is required"));
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

    @ExceptionHandler(NewApiException.class)
    public ResponseEntity<Map<String, String>> newApiFailure() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("code", "NEWAPI_ERROR", "message", "Upstream request failed"));
    }
}
