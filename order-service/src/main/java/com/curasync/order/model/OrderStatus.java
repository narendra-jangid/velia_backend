package com.curasync.order.model;

/**
 * Reference list of valid fulfilment statuses. The Order document stores
 * status as a plain String (see Order.java) so the JSON casing matches the
 * Velia Next.js admin panel exactly ("Confirmed", not "CONFIRMED") — this
 * enum exists for documentation and server-side validation only.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
