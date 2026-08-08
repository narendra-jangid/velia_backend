package com.curasync.order.model;

/**
 * Reference list of valid payment statuses (stored as a plain String on
 * Order.paymentStatus, lowercase).
 */
public enum PaymentStatus {
    PENDING,
    PAID,
    COD_PENDING,
    FAILED
}
