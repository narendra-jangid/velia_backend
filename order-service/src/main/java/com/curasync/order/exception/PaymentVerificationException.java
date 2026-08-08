package com.curasync.order.exception;

/** Thrown when a Razorpay payment signature fails HMAC verification. */
public class PaymentVerificationException extends RuntimeException {
    public PaymentVerificationException(String message) {
        super(message);
    }
}
