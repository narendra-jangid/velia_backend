package com.curasync.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Feign client targeting product-service via Eureka service name.
 * Uses the same endpoint paths as ProductController.
 */
@FeignClient(name = "product-service", fallback = ProductClientFallback.class)
public interface ProductClient {

    @GetMapping("/api/products/{id}")
    Map<String, Object> getProductById(@PathVariable("id") String id);

    @PostMapping(value = "/api/products/{id}/reduce-stock",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> reduceStock(@PathVariable("id") String id,
                                    @RequestBody Map<String, Object> body);
}