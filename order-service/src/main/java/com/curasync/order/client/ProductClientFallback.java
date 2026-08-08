package com.curasync.order.client;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ProductClientFallback implements ProductClient {

    @Override
    public Map<String, Object> getProductById(String id) {
        return null; // triggers ProductUnavailableException in OrderService
    }

    @Override
    public Map<String, Object> reduceStock(String id, Map<String, Object> body) {
        return null;
    }
}