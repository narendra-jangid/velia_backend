package com.curasync.order.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Order document stored in the shared MongoDB Atlas cluster.
 * Collection: "orders"
 *
 * Mirrors the cart-checkout payload sent by the Velia Next.js Checkout
 * page — one order holds multiple line items (see OrderItem), plus full
 * customer/shipping/payment details. Product fields inside each item are
 * snapshotted at order time so historical orders stay accurate even if a
 * product is later edited.
 */
@Document(collection = "orders")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Order {

    @Id
    private String id;

    // Human-readable order number shown to the customer/admin, e.g. "VL-10023456"
    @Indexed(unique = true)
    private String orderId;

    // ── Line items ───────────────────────────────────────────────
    private List<OrderItem> items;

    // ── Customer / shipping info ────────────────────────────────
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String shippingAddress;
    private String city;
    private String state;
    private String pincode;

    // ── Pricing (server-calculated from live product prices — never
    // trust amounts sent by the client) ─────────────────────────
    private Double subtotal;
    private Double discount;
    private Double delivery;
    private Double total;

    // ── Payment (see PaymentMethod / PaymentStatus reference values) ──
    private String paymentMethod;   // cod | razorpay | card | upi
    private String paymentStatus;   // pending | paid | cod_pending | failed

    // ── Razorpay linkage — set once a Razorpay order is created /
    // payment is verified. Null for COD orders. ───────────────────
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;

    // ── Fulfilment ───────────────────────────────────────────────
    private String status;          // Pending | Confirmed | Shipped | Delivered | Cancelled

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
