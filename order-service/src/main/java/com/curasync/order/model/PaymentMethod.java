package com.curasync.order.model;

/**
 * Reference list of valid payment methods (stored as a plain String on
 * Order.paymentMethod, lowercase, matching the Next.js checkout payload).
 * Only COD is accepted server-side at launch — see OrderService.placeOrder.
 */
public enum PaymentMethod {
    COD,
    RAZORPAY,
    CARD,
    UPI
}
