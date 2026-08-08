package com.curasync.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Service Discovery Server.
 *
 * All microservices register themselves here on startup, and use this
 * registry to discover each other's network locations (host:port) instead
 * of hardcoding URLs. This is the foundation that makes the API Gateway's
 * load-balanced routing and Feign client service-to-service calls possible.
 *
 * Dashboard available at: http://localhost:8761
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
