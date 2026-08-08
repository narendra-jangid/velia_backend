package com.curasync.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Fallback endpoints invoked by the CircuitBreaker filter (see
 * application.yml) when product-service or order-service is unreachable,
 * slow, or returning errors. Instead of letting the caller hang or get a
 * raw connection-refused error, we return a clean, predictable response.
 *
 * This is the Circuit Breaker / Graceful Degradation pattern in action.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/products")
    public ResponseEntity<Map<String, Object>> productServiceFallback() {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "error",
                        "message", "Product Service is currently unavailable. Please try again shortly.",
                        "service", "product-service"
                ));
    }

    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> orderServiceFallback() {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "error",
                        "message", "Order Service is currently unavailable. Please try again shortly.",
                        "service", "order-service"
                ));
    }

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> userServiceFallback() {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "error",
                        "message", "User Service is currently unavailable. Please try again shortly.",
                        "service", "user-service"
                ));
    }
}
