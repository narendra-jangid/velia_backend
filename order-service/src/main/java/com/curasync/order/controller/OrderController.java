package com.curasync.order.controller;

import com.curasync.order.exception.PaymentVerificationException;
import com.curasync.order.model.Order;
import com.curasync.order.service.OrderService;
import com.curasync.order.service.RazorpayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final RazorpayService razorpayService;

    public OrderController(OrderService orderService, RazorpayService razorpayService) {
        this.orderService = orderService;
        this.razorpayService = razorpayService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll() {
        List<Order> orders = orderService.getAllOrders();
        return ResponseEntity.ok(Map.of("success", true, "count", orders.size(), "data", orders));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("success", true, "data", orderService.getOrderById(id)));
    }

    @GetMapping("/by-email/{email}")
    public ResponseEntity<Map<String, Object>> getByEmail(@PathVariable String email) {
        List<Order> orders = orderService.getOrdersByEmail(email);
        return ResponseEntity.ok(Map.of("success", true, "count", orders.size(), "data", orders));
    }

    // Used by the Velia "My Orders" page — customers look up past orders
    // by the same phone number they logged in with.
    @GetMapping("/by-phone/{phone}")
    public ResponseEntity<Map<String, Object>> getByPhone(@PathVariable String phone) {
        List<Order> orders = orderService.getOrdersByPhone(phone);
        return ResponseEntity.ok(Map.of("success", true, "count", orders.size(), "data", orders));
    }

    // Cart checkout — body shape documented in OrderService.placeOrder().
    // For paymentMethod "razorpay" the response includes a "razorpayOrder"
    // object the frontend uses to open the Razorpay checkout widget.
    @PostMapping("/place")
    public ResponseEntity<Map<String, Object>> placeOrder(@RequestBody Map<String, Object> req) {
        OrderService.PlaceOrderResult result = orderService.placeOrder(req);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "Order placed");
        body.put("orderId", result.order().getOrderId());
        body.put("data", result.order());
        if (result.razorpayOrder() != null) {
            body.put("razorpayOrder", result.razorpayOrder());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // Admin panel — move an order through Pending → Confirmed → Shipped → Delivered (or Cancelled).
    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable String id, @RequestBody Map<String, Object> req) {
        String status = req.get("status") != null ? req.get("status").toString() : null;
        Order order = orderService.updateStatus(id, status);
        return ResponseEntity.ok(Map.of("success", true, "message", "Status updated", "data", order));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("success", true, "message", "Order cancelled",
                "data", orderService.cancelOrder(id)));
    }

    // ── Razorpay: verify payment ──────────────────────────────────
    // Called by the frontend right after Razorpay's checkout widget hands
    // back a payment result. Body: { orderId, razorpay_order_id,
    // razorpay_payment_id, razorpay_signature }. orderId is OUR
    // human-readable order id (Order.orderId), not the Mongo _id.
    @PostMapping("/razorpay/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(@RequestBody Map<String, Object> req) {
        String orderId = str(req, "orderId");
        String razorpayOrderId = str(req, "razorpay_order_id");
        String razorpayPaymentId = str(req, "razorpay_payment_id");
        String razorpaySignature = str(req, "razorpay_signature");

        if (orderId == null || razorpayOrderId == null || razorpayPaymentId == null || razorpaySignature == null) {
            throw new PaymentVerificationException("Missing payment verification fields.");
        }

        Order order = orderService.verifyPayment(orderId, razorpayOrderId, razorpayPaymentId, razorpaySignature);
        return ResponseEntity.ok(Map.of("success", true, "message", "Payment verified", "data", order));
    }

    // ── Razorpay: client reports a failed / cancelled payment attempt ──
    // Best-effort UX signal only — the webhook below is the source of
    // truth for actual payment state.
    @PostMapping("/razorpay/payment-failed")
    public ResponseEntity<Map<String, Object>> paymentFailed(@RequestBody Map<String, Object> req) {
        String orderId = str(req, "orderId");
        if (orderId == null) {
            throw new PaymentVerificationException("Missing orderId.");
        }
        Order order = orderService.markPaymentFailed(orderId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Payment marked failed", "data", order));
    }

    // ── Razorpay: webhook ────────────────────────────────────────
    // Configure this URL (https://<your-domain>/api/orders/razorpay/webhook)
    // in the Razorpay Dashboard → Settings → Webhooks, subscribed to at
    // least "payment.captured" and "payment.failed". Signature is verified
    // against the RAW request body using RAZORPAY_WEBHOOK_SECRET before
    // anything in the payload is trusted.
    @PostMapping(value = "/razorpay/webhook", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        if (!razorpayService.isWebhookConfigured()) {
            log.warn("Razorpay webhook received but RAZORPAY_WEBHOOK_SECRET is not configured — ignoring.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("success", false, "message", "Webhook not configured"));
        }

        if (!razorpayService.verifyWebhookSignature(rawBody, signature)) {
            log.warn("Razorpay webhook signature verification failed — ignoring payload.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Invalid webhook signature"));
        }

        Map<String, Object> event = razorpayService.parseWebhookBody(rawBody);
        orderService.handleWebhookEvent(event);

        return ResponseEntity.ok(Map.of("success", true));
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
