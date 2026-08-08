package com.curasync.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Talks to Razorpay's Orders API directly over REST (Basic Auth built from
 * key id/secret — the same API the official SDK calls under the hood) and
 * verifies payment/webhook signatures using HMAC-SHA256, per Razorpay's
 * official verification scheme:
 * https://razorpay.com/docs/payments/server-integration/java/payments/#step-3-verify-payment-signature
 *
 * RAZORPAY_KEY_SECRET / RAZORPAY_WEBHOOK_SECRET never leave this service —
 * they are read from env vars only and are never returned in any API
 * response or logged.
 */
@Service
public class RazorpayService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayService.class);
    private static final String ORDERS_URL = "https://api.razorpay.com/v1/orders";
    private static final String HMAC_ALGO = "HmacSHA256";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${razorpay.key-id:}")
    private String keyId;

    @Value("${razorpay.key-secret:}")
    private String keySecret;

    @Value("${razorpay.webhook-secret:}")
    private String webhookSecret;

    public RazorpayService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }

    public boolean isWebhookConfigured() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    /**
     * Creates a Razorpay order. amountPaise must be the smallest currency
     * unit (paise for INR) — e.g. ₹499.50 => 49950. Returns Razorpay's raw
     * order object (id, amount, currency, receipt, status, ...).
     */
    public Map<String, Object> createOrder(long amountPaise, String currency, String receipt) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Razorpay is not configured — set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(keyId, keySecret);

        Map<String, Object> body = new HashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", currency);
        body.put("receipt", receipt);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(ORDERS_URL, HttpMethod.POST, entity, Map.class);
            Map<String, Object> result = response.getBody();
            log.info("Razorpay order created: receipt={}, razorpayOrderId={}",
                    receipt, result != null ? result.get("id") : null);
            return result;
        } catch (RestClientException ex) {
            log.error("Razorpay order creation failed for receipt {}: {}", receipt, ex.getMessage());
            throw new IllegalStateException("Failed to create Razorpay order: " + ex.getMessage(), ex);
        }
    }

    /**
     * Verifies the (razorpay_order_id, razorpay_payment_id, razorpay_signature)
     * triple Razorpay's checkout.js hands back to the client after payment.
     * Formula per Razorpay docs: HMAC-SHA256("{order_id}|{payment_id}", key_secret).
     */
    public boolean verifyPaymentSignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        if (!isConfigured() || razorpayOrderId == null || razorpayPaymentId == null || razorpaySignature == null) {
            return false;
        }
        String payload = razorpayOrderId + "|" + razorpayPaymentId;
        String expected = hmacHex(payload, keySecret);
        return constantTimeEquals(expected, razorpaySignature);
    }

    /**
     * Verifies a Razorpay webhook request using the raw request body and the
     * X-Razorpay-Signature header, per:
     * https://razorpay.com/docs/webhooks/validate-test/
     */
    public boolean verifyWebhookSignature(String rawBody, String signatureHeader) {
        if (!isWebhookConfigured() || rawBody == null || signatureHeader == null) {
            return false;
        }
        String expected = hmacHex(rawBody, webhookSecret);
        return constantTimeEquals(expected, signatureHeader);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseWebhookBody(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, Map.class);
        } catch (Exception ex) {
            log.error("Failed to parse Razorpay webhook body: {}", ex.getMessage());
            throw new IllegalStateException("Invalid webhook payload");
        }
    }

    private String hmacHex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute HMAC signature", ex);
        }
    }

    /** Timing-attack-resistant comparison — never use String.equals() for signatures. */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
