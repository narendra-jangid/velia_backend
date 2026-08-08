package com.curasync.user.controller;

import com.curasync.user.service.CartService;
import com.curasync.user.util.PhoneValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users/{phone}/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getCart(@PathVariable String phone) {
        validatePhone(phone);
        return ResponseEntity.ok(Map.of("success", true, "data", cartService.getCart(phone)));
    }

    /** Body: { "productId", "name", "img", "price", "qty", "size", "color" } — id alias supported */
    @PostMapping
    public ResponseEntity<Map<String, Object>> addItem(
            @PathVariable String phone,
            @RequestBody Map<String, Object> request) {
        validatePhone(phone);
        return ResponseEntity.ok(Map.of("success", true, "data", cartService.addItem(phone, request)));
    }

    /** Body: any subset of { "qty", "size", "color", "name", "img", "price" } */
    @PutMapping("/{productId}")
    public ResponseEntity<Map<String, Object>> updateItem(
            @PathVariable String phone,
            @PathVariable String productId,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String color,
            @RequestBody Map<String, Object> request) {
        validatePhone(phone);
        if (size != null)  request.putIfAbsent("size", size);
        if (color != null) request.putIfAbsent("color", color);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", cartService.updateItem(phone, productId, request)));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Map<String, Object>> removeItem(
            @PathVariable String phone,
            @PathVariable String productId,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String color) {
        validatePhone(phone);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", cartService.removeItem(phone, productId, size, color)));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clearCart(@PathVariable String phone) {
        validatePhone(phone);
        return ResponseEntity.ok(Map.of("success", true, "data", cartService.clearCart(phone)));
    }

    private void validatePhone(String phone) {
        if (!PhoneValidator.isValid(phone)) {
            throw new IllegalStateException("Valid 10-digit phone required");
        }
    }
}
