package com.curasync.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API Gateway - single entry point for all client requests.
 *
 * Routes incoming HTTP requests to the correct downstream microservice
 * based on path prefixes (see application.yml), using Eureka for
 * service discovery so it never needs hardcoded instance URLs.
 *
 * All external traffic should go through this gateway on port 8080
 * rather than calling product-service / order-service directly.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
