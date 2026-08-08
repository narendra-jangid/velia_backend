package com.curasync.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Velia User Service.
 *
 * Stores customer profiles keyed by phone number — created (or fetched)
 * the moment OTP verification succeeds in the Next.js app, then used to
 * prefill the checkout form and power "My Orders" lookups.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
