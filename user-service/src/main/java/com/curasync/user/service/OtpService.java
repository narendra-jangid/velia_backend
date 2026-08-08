package com.curasync.user.service;

import com.curasync.user.model.OtpRecord;
import com.curasync.user.repository.OtpRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class OtpService {

    private static final int MAX_ATTEMPTS = 5;

    private final OtpRepository otpRepository;
    private final SecureRandom random = new SecureRandom();
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Value("${otp.expiry-minutes:5}")
    private int expiryMinutes;

    /** When true, OTP is included in the API response (dev only). */
    @Value("${otp.expose-in-response:true}")
    private boolean exposeInResponse;

    public OtpService(OtpRepository otpRepository) {
        this.otpRepository = otpRepository;
    }

    /**
     * Generates a 6-digit OTP, stores it with expiry, and logs it.
     * In production, wire an SMS provider (Twilio, MSG91, etc.) here.
     */
    public String sendOtp(String phone) {
        String otp = String.format("%06d", random.nextInt(1_000_000));

        OtpRecord record = otpRepository.findByPhone(phone)
                .orElse(OtpRecord.builder().phone(phone).build());

        record.setOtp(otp);
        record.setExpiresAt(Instant.now().plus(expiryMinutes, ChronoUnit.MINUTES));
        record.setAttempts(0);
        otpRepository.save(record);

        log.info("OTP generated for {} (expires in {} min)", phone, expiryMinutes);
        log.debug("OTP value for {}: {}", phone, otp); // DEBUG only — never at INFO+, same as any other secret
        return exposeInResponse ? otp : null;
    }

    public boolean verifyOtp(String phone, String otp) {
        OtpRecord record = otpRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalStateException("No OTP found for this phone. Request a new one."));

        if (record.getAttempts() >= MAX_ATTEMPTS) {
            otpRepository.delete(record);
            log.warn("OTP verification blocked for {}: too many failed attempts", phone);
            throw new IllegalStateException("Too many failed attempts. Request a new OTP.");
        }

        if (Instant.now().isAfter(record.getExpiresAt())) {
            otpRepository.delete(record);
            log.info("OTP verification failed for {}: expired", phone);
            throw new IllegalStateException("OTP has expired. Request a new one.");
        }

        if (!record.getOtp().equals(otp)) {
            record.setAttempts(record.getAttempts() + 1);
            otpRepository.save(record);
            log.info("OTP verification failed for {}: incorrect code (attempt {}/{})", phone, record.getAttempts(), MAX_ATTEMPTS);
            throw new IllegalStateException("Invalid OTP.");
        }

        otpRepository.delete(record);
        log.info("OTP verified successfully for {}", phone);
        return true;
    }

}
