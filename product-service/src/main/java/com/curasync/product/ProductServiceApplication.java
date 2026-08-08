package com.curasync.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Velia Product Service.
 * Connects to the shared MongoDB Atlas cluster (same DB the Next.js admin
 * panel uses) — reads and writes the "products" collection directly.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
