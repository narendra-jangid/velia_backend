package com.curasync.order.service;

import com.curasync.order.client.ProductClient;
import com.curasync.order.exception.OrderNotFoundException;
import com.curasync.order.exception.PaymentVerificationException;
import com.curasync.order.exception.ProductUnavailableException;
import com.curasync.order.model.Order;
import com.curasync.order.model.OrderItem;
import com.curasync.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private static final List<String> VALID_STATUSES =
            List.of("Pending", "Confirmed", "Shipped", "Delivered", "Cancelled");

    // Card/UPI are modelled (see PaymentMethod) but rejected server-side
    // until they have their own gateway integration — matches the Velia
    // Next.js Checkout page, which also only enables COD + Razorpay.
    private static final List<String> VALID_PAYMENT_METHODS = List.of("cod", "razorpay");

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final RazorpayService razorpayService;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public OrderService(OrderRepository orderRepository, ProductClient productClient, RazorpayService razorpayService) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.razorpayService = razorpayService;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public Order getOrderById(String id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public Order getOrderByOrderId(String orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    public List<Order> getOrdersByEmail(String email) {
        return orderRepository.findByCustomerEmail(email);
    }

    public List<Order> getOrdersByPhone(String phone) {
        return orderRepository.findByCustomerPhoneOrderByCreatedAtDesc(phone);
    }

    /**
     * Result of placeOrder(): the persisted order, plus (for Razorpay only)
     * the Razorpay order object the frontend needs to open the checkout
     * widget — {id, amount, currency, ...}. Null for COD.
     */
    public record PlaceOrderResult(Order order, Map<String, Object> razorpayOrder) {}

    /**
     * Places a cart-checkout order.
     *
     * Expected request shape (matches the Velia Next.js Checkout page payload):
     * {
     *   "customer": { "name", "phone", "email", "address", "city", "state", "pincode" },
     *   "items": [ { "id", "qty", "size", "color" }, ... ],
     *   "delivery", "discount",
     *   "paymentMethod": "cod" | "razorpay"
     * }
     *
     * For each line item: fetches the product from Product Service via Feign,
     * validates it's active with enough stock, then reduces stock. Price and
     * name are always read from Product Service — client-supplied price/
     * subtotal/total values are ignored entirely so a tampered request can
     * never change what's actually charged. If any item fails validation the
     * whole order is rejected — stock reductions already applied to earlier
     * items in the loop are NOT auto-rolled-back (acceptable trade-off at
     * launch scale; revisit with a saga/outbox if order volume grows).
     *
     * For "razorpay": the order is persisted first with paymentStatus
     * "pending", then a Razorpay order is created against the server-
     * calculated total and linked back onto the order. The order is only
     * ever marked "paid" by verifyPayment()/handleWebhookEvent() — never
     * here, since payment hasn't happened yet at this point.
     */
    @SuppressWarnings("unchecked")
    public PlaceOrderResult placeOrder(Map<String, Object> request) {

        Object customerObj = request.get("customer");
        Object itemsObj = request.get("items");

        if (!(customerObj instanceof Map) || !(itemsObj instanceof List) || ((List<?>) itemsObj).isEmpty()) {
            throw new IllegalStateException("Order must include a customer and at least one item.");
        }

        Map<String, Object> customer = (Map<String, Object>) customerObj;
        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) itemsObj;

        String name = str(customer, "name", null);
        String phone = str(customer, "phone", null);
        String address = str(customer, "address", null);
        String city = str(customer, "city", null);
        String state = str(customer, "state", null);
        String pincode = resolvePincode(customer);

        if (name == null || phone == null || address == null || city == null || state == null || pincode == null) {
            throw new IllegalStateException("Customer name, phone, address, city, state and pincode are all required.");
        }

        if (!pincode.matches("\\d{6}")) {
            throw new IllegalStateException("Valid 6-digit pincode required.");
        }

        String paymentMethod = str(request, "paymentMethod", "cod");
        if (!VALID_PAYMENT_METHODS.contains(paymentMethod)) {
            throw new IllegalStateException(
                    "Payment method '" + paymentMethod + "' isn't available. Please use Cash on Delivery or Razorpay.");
        }

        if ("razorpay".equals(paymentMethod) && !razorpayService.isConfigured()) {
            throw new IllegalStateException("Razorpay isn't configured on the server right now. Please use Cash on Delivery.");
        }

        // ── Validate + price every item straight from Product Service ──
        // (the source of truth) — nothing here comes from the request body.
        List<OrderItem> orderItems = new ArrayList<>();

        for (Map<String, Object> raw : rawItems) {
            String productId = raw.get("id") != null ? raw.get("id").toString() : null;
            if (productId == null) {
                throw new IllegalStateException("Each item must include a product id.");
            }

            int qty = raw.get("qty") != null ? Integer.parseInt(raw.get("qty").toString()) : 1;
            if (qty < 1) {
                throw new IllegalStateException("Quantity must be at least 1.");
            }

            Map<String, Object> product = productClient.getProductById(productId);
            if (product == null) {
                throw new ProductUnavailableException("Product Service unavailable. Cannot place order right now.");
            }

            boolean active = product.get("active") != null && Boolean.parseBoolean(product.get("active").toString());
            int stock = product.get("stock") != null ? Integer.parseInt(product.get("stock").toString()) : 0;
            String productName = product.get("name") != null ? product.get("name").toString() : "Unknown Product";
            double price = product.get("price") != null ? Double.parseDouble(product.get("price").toString()) : 0.0;
            String thumbnail = product.get("thumbnail") != null ? product.get("thumbnail").toString() : null;

            if (!active) {
                throw new IllegalStateException("Product '" + productName + "' is not available.");
            }
            if (stock < qty) {
                throw new IllegalStateException(
                        "Insufficient stock for '" + productName + "'. Available: " + stock + ", Requested: " + qty);
            }

            try {
                productClient.reduceStock(productId, Map.of("quantity", qty));
            } catch (Exception ex) {
                log.error("Stock reduction failed for {}: {}", productId, ex.getMessage());
                throw new IllegalStateException("Stock update failed for '" + productName + "': " + ex.getMessage());
            }

            orderItems.add(OrderItem.builder()
                    .productId(productId)
                    .name(productName)
                    .img(thumbnail)
                    .price(price)
                    .qty(qty)
                    .size(raw.get("size") != null ? raw.get("size").toString() : null)
                    .color(raw.get("color") != null ? raw.get("color").toString() : null)
                    .build());
        }

        // ── Server-calculated totals — the only numbers that matter ────
        double subtotal = 0;
        for (OrderItem item : orderItems) {
            subtotal += item.getPrice() * item.getQty();
        }

        double delivery = numOrDefault(request, "delivery", subtotal > 999 ? 0 : 79);
        double discount = numOrDefault(request, "discount", 0);
        double total = Math.max(0, subtotal + delivery - discount);

        Order order = Order.builder()
                .orderId(generateOrderId())
                .items(orderItems)
                .customerName(name)
                .customerPhone(phone)
                .customerEmail(str(customer, "email", null))
                .shippingAddress(address)
                .city(city)
                .state(state)
                .pincode(pincode)
                .subtotal(round2(subtotal))
                .discount(round2(discount))
                .delivery(round2(delivery))
                .total(round2(total))
                .paymentMethod(paymentMethod)
                .paymentStatus("cod".equals(paymentMethod) ? "cod_pending" : "pending")
                .status("cod".equals(paymentMethod) ? "Confirmed" : "Pending")
                .build();

        Order saved = orderRepository.save(order);
        log.info("Order placed: orderId={}, paymentMethod={}, total={}", saved.getOrderId(), paymentMethod, saved.getTotal());

        if (!"razorpay".equals(paymentMethod)) {
            return new PlaceOrderResult(saved, null);
        }

        // ── Create the Razorpay order against the server-calculated total ──
        long amountPaise = Math.round(saved.getTotal() * 100);
        Map<String, Object> razorpayOrder;
        try {
            razorpayOrder = razorpayService.createOrder(amountPaise, "INR", saved.getOrderId());
        } catch (Exception ex) {
            // Order already exists (and stock already reduced) — leave it as
            // "pending" rather than deleting it; the customer can retry
            // payment for the same orderId, or an admin can cancel it.
            log.error("Razorpay order creation failed for orderId={}: {}", saved.getOrderId(), ex.getMessage());
            throw new IllegalStateException("Failed to start Razorpay payment: " + ex.getMessage());
        }

        saved.setRazorpayOrderId(razorpayOrder.get("id") != null ? razorpayOrder.get("id").toString() : null);
        saved = orderRepository.save(saved);

        return new PlaceOrderResult(saved, razorpayOrder);
    }

    /**
     * Verifies a Razorpay payment signature and, if valid, marks the order
     * paid. Idempotent — a repeat call for an already-paid order is a no-op
     * success rather than reprocessing (protects against duplicate/retry
     * requests from the client or overlapping webhook delivery).
     */
    public Order verifyPayment(String orderId, String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        Order order = getOrderByOrderId(orderId);

        if ("paid".equals(order.getPaymentStatus())) {
            log.info("verifyPayment: orderId={} already paid — skipping reprocessing.", orderId);
            return order;
        }

        if (order.getRazorpayOrderId() == null || !order.getRazorpayOrderId().equals(razorpayOrderId)) {
            throw new PaymentVerificationException("Razorpay order id does not match this order.");
        }

        boolean valid = razorpayService.verifyPaymentSignature(razorpayOrderId, razorpayPaymentId, razorpaySignature);
        if (!valid) {
            log.warn("Payment signature verification FAILED for orderId={}", orderId);
            throw new PaymentVerificationException("Payment signature verification failed.");
        }

        order.setPaymentStatus("paid");
        order.setRazorpayPaymentId(razorpayPaymentId);
        order.setRazorpaySignature(razorpaySignature);
        order.setStatus("Confirmed");

        Order saved = orderRepository.save(order);
        log.info("Payment verified and order confirmed: orderId={}, razorpayPaymentId={}", orderId, razorpayPaymentId);
        return saved;
    }

    /**
     * Marks a Razorpay order's payment attempt as failed (e.g. the customer
     * cancelled the checkout widget, or Razorpay reported a failure). Does
     * NOT restore stock automatically — see class-level note on stock
     * trade-offs; cancel the order separately if stock should be released.
     */
    public Order markPaymentFailed(String orderId) {
        Order order = getOrderByOrderId(orderId);
        if ("paid".equals(order.getPaymentStatus())) {
            log.warn("markPaymentFailed called for orderId={} but it is already paid — ignoring.", orderId);
            return order;
        }
        order.setPaymentStatus("failed");
        Order saved = orderRepository.save(order);
        log.info("Payment marked failed for orderId={}", orderId);
        return saved;
    }

    /**
     * Handles a verified Razorpay webhook event body (see controller for
     * signature verification, which must happen before this is called).
     * Idempotent for the same reasons as verifyPayment().
     */
    @SuppressWarnings("unchecked")
    public void handleWebhookEvent(Map<String, Object> event) {
        String eventType = event.get("event") != null ? event.get("event").toString() : null;
        if (eventType == null) {
            log.warn("Webhook event missing 'event' field — ignoring.");
            return;
        }

        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        Map<String, Object> paymentEntity = payload != null ? (Map<String, Object>) payload.get("payment") : null;
        Map<String, Object> payment = paymentEntity != null ? (Map<String, Object>) paymentEntity.get("entity") : null;
        if (payment == null) {
            log.warn("Webhook event {} missing payment entity — ignoring.", eventType);
            return;
        }

        String razorpayOrderId = payment.get("order_id") != null ? payment.get("order_id").toString() : null;
        String razorpayPaymentId = payment.get("id") != null ? payment.get("id").toString() : null;
        if (razorpayOrderId == null) {
            log.warn("Webhook event {} missing order_id — ignoring.", eventType);
            return;
        }

        Order order = orderRepository.findAll().stream()
                .filter(o -> razorpayOrderId.equals(o.getRazorpayOrderId()))
                .findFirst()
                .orElse(null);

        if (order == null) {
            log.warn("Webhook event {}: no order found for razorpayOrderId={}", eventType, razorpayOrderId);
            return;
        }

        switch (eventType) {
            case "payment.captured" -> {
                if ("paid".equals(order.getPaymentStatus())) {
                    log.info("Webhook payment.captured: orderId={} already paid — skipping.", order.getOrderId());
                    return;
                }
                order.setPaymentStatus("paid");
                order.setRazorpayPaymentId(razorpayPaymentId);
                order.setStatus("Confirmed");
                orderRepository.save(order);
                log.info("Webhook payment.captured: orderId={} marked paid.", order.getOrderId());
            }
            case "payment.failed" -> {
                if (!"paid".equals(order.getPaymentStatus())) {
                    order.setPaymentStatus("failed");
                    orderRepository.save(order);
                    log.info("Webhook payment.failed: orderId={} marked failed.", order.getOrderId());
                }
            }
            default -> log.info("Webhook event {} received for orderId={} — no handler, ignoring.", eventType, order.getOrderId());
        }
    }

    public Order updateStatus(String id, String status) {
        if (status == null || !VALID_STATUSES.contains(status)) {
            throw new IllegalStateException("Invalid status: " + status);
        }
        Order order = getOrderById(id);
        order.setStatus(status);
        return orderRepository.save(order);
    }

    public Order cancelOrder(String id) {
        Order order = getOrderById(id);
        if ("Cancelled".equals(order.getStatus())) {
            throw new IllegalStateException("Order " + id + " is already cancelled.");
        }
        order.setStatus("Cancelled");
        return orderRepository.save(order);
    }

    // ── helpers ──────────────────────────────────────────────────

    private String generateOrderId() {
        return "VL-" + (System.currentTimeMillis() % 100000000L);
    }

    private String str(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        if (v == null) return fallback;
        String s = v.toString().trim();
        return s.isEmpty() ? fallback : s;
    }

    private double numOrDefault(Map<String, Object> map, String key, double fallback) {
        Object v = map.get(key);
        if (v == null) return fallback;
        try { return Double.parseDouble(v.toString()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String resolvePincode(Map<String, Object> customer) {
        String pincode = str(customer, "pincode", null);
        if (pincode == null) pincode = str(customer, "zip", null);
        if (pincode == null) pincode = str(customer, "zipcode", null);
        return pincode;
    }
}
