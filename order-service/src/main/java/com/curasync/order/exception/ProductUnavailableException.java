package com.curasync.order.exception;

/**
 * Thrown when Product Service cannot be reached (circuit breaker fallback
 * returned null) or rejects the request — e.g. insufficient stock.
 */
public class ProductUnavailableException extends RuntimeException {
    public ProductUnavailableException(String message) {
        super(message);
    }
}
