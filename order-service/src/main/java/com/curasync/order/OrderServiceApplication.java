package com.curasync.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Order Service — owns order data.
 *
 * Demonstrates synchronous inter-service communication: when an order is
 * placed, this service calls Product Service (via the ProductClient Feign
 * interface) to validate and reduce stock before persisting the order.
 *
 * @EnableFeignClients scans for interfaces annotated with @FeignClient
 * and generates the HTTP client implementation at runtime.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
