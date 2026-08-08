package com.curasync.order.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(OrderNotFoundException ex) {
        log.warn("Order not found: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ProductUnavailableException.class)
    public ResponseEntity<Map<String, Object>> unavailable(ProductUnavailableException ex) {
        log.error("Product Service unavailable while processing order request", ex);
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(PaymentVerificationException.class)
    public ResponseEntity<Map<String, Object>> paymentVerification(PaymentVerificationException ex) {
        // Deliberately WARN, not ERROR — a failed verification can be a normal
        // event (stale/duplicate client retry) as well as a genuine attack
        // attempt; either way it's not a server bug, but it must be visible.
        log.warn("Payment signature verification rejected: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException ex) {
        log.warn("Request rejected: {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + ", " + b);
        log.warn("Validation failed: {}", msg);
        return error(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> generic(Exception ex) {
        // Full stack trace, always — this is the catch-all for anything
        // unexpected (NPEs, Mongo errors, Feign failures that slipped through,
        // etc.) and is exactly the case where losing the trace hurts most.
        log.error("Unhandled exception while processing request", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success",   false);
        body.put("timestamp", Instant.now().toString());
        body.put("status",    status.value());
        body.put("message",   message);
        return ResponseEntity.status(status).body(body);
    }
}
