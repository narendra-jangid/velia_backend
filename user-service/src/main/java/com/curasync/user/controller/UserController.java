package com.curasync.user.controller;

import com.curasync.user.model.User;
import com.curasync.user.service.OtpService;
import com.curasync.user.service.UserService;
import com.curasync.user.util.PhoneValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final OtpService otpService;

    public UserController(UserService userService, OtpService otpService) {
        this.userService = userService;
        this.otpService = otpService;
    }

    // ── OTP flow ─────────────────────────────────────────────────

    /** Body: { "phone": "9876543210" } */
    @PostMapping("/otp/send")
    public ResponseEntity<Map<String, Object>> sendOtp(@RequestBody Map<String, Object> request) {
        String phone = normalizePhone(request);
        if (!PhoneValidator.isValid(phone)) {
            log.warn("OTP send rejected: invalid phone");
            return badRequest("Valid 10-digit phone required");
        }

        String otp = otpService.sendOtp(phone); // OtpService itself logs — never log the raw OTP here
        log.info("POST /api/users/otp/send — OTP dispatched for {}", phone);
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "OTP sent successfully");
        if (otp != null) body.put("otp", otp); // dev only — controlled by otp.expose-in-response; remove for production
        return ResponseEntity.ok(body);
    }

    /** Body: { "phone": "9876543210", "otp": "123456", "name": "optional" } */
    @PostMapping("/otp/verify")
    public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody Map<String, Object> request) {
        String phone = normalizePhone(request);
        String otp   = request.get("otp")  != null ? request.get("otp").toString()  : null;
        String name  = request.get("name") != null ? request.get("name").toString() : null;

        if (!PhoneValidator.isValid(phone)) {
            return badRequest("Valid 10-digit phone required");
        }
        if (otp == null || !otp.matches("\\d{6}")) {
            return badRequest("Valid 6-digit OTP required");
        }

        otpService.verifyOtp(phone, otp);
        User user = userService.verifyAndUpsert(phone, name);
        log.info("POST /api/users/otp/verify — login successful for {}", phone);
        return ResponseEntity.ok(Map.of("success", true, "data", user));
    }

    /** Legacy endpoint — kept for backward compatibility with existing Next.js routes. */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody Map<String, Object> request) {
        String phone = normalizePhone(request);
        String name  = request.get("name") != null ? request.get("name").toString() : null;

        if (!PhoneValidator.isValid(phone)) {
            return badRequest("Valid 10-digit phone required");
        }

        User user = userService.verifyAndUpsert(phone, name);
        return ResponseEntity.ok(Map.of("success", true, "data", user));
    }

    // ── Profile ──────────────────────────────────────────────────

    @GetMapping("/{phone}")
    public ResponseEntity<Map<String, Object>> getByPhone(@PathVariable String phone) {
        if (!PhoneValidator.isValid(phone)) {
            return badRequest("Valid 10-digit phone required");
        }
        return ResponseEntity.ok(Map.of("success", true, "data", userService.getByPhone(phone)));
    }

    @PutMapping("/{phone}")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @PathVariable String phone,
            @RequestBody Map<String, Object> request) {
        if (!PhoneValidator.isValid(phone)) {
            return badRequest("Valid 10-digit phone required");
        }
        User updated = userService.updateProfile(phone, request);
        return ResponseEntity.ok(Map.of("success", true, "data", updated));
    }

    // ── helpers ────────────────────────────────────────────────────

    private String normalizePhone(Map<String, Object> request) {
        Object raw = request.get("phone");
        return raw == null ? null : PhoneValidator.normalize(raw.toString());
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", message));
    }
}
