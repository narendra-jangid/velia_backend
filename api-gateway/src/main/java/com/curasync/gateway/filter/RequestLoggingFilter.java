package com.curasync.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Logs every request that passes through the gateway — method, path, which
 * downstream route it matched, response status, and latency — plus
 * generates an X-Request-Id (if the client didn't send one) and propagates
 * it to the downstream service, so a single request can be traced across
 * gateway → service logs by grepping for that id.
 *
 * This is the single highest-leverage logging addition for a microservices
 * setup like this one: every request to every service passes through here
 * exactly once, so it's "one place to monitor" without needing a full
 * log-aggregation stack (ELK/Loki/etc — worth adding later if traffic grows,
 * out of scope for this pass).
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }
        final String reqId = requestId;

        // Propagate downstream so order-service/product-service/user-service
        // logs can be correlated back to this exact gateway entry.
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(REQUEST_ID_HEADER, reqId)
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getURI().getPath();
        String clientIp = request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

        long start = System.currentTimeMillis();
        log.info("[REQ-{}] Incoming {} {} from {}", reqId, method, path, clientIp);

        return chain.filter(mutatedExchange).doFinally(signalType -> {
            long durationMs = System.currentTimeMillis() - start;
            ServerHttpResponse response = mutatedExchange.getResponse();
            int status = response.getStatusCode() != null ? response.getStatusCode().value() : -1;

            Object routeAttr = mutatedExchange.getAttributes().get(
                    org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String routeId = routeAttr != null ? routeAttr.toString() : "no-route-matched";

            if (status >= 500) {
                log.error("[REQ-{}] {} {} -> {} ({}ms) route={}", reqId, method, path, status, durationMs, routeId);
            } else if (status >= 400) {
                log.warn("[REQ-{}] {} {} -> {} ({}ms) route={}", reqId, method, path, status, durationMs, routeId);
            } else {
                log.info("[REQ-{}] {} {} -> {} ({}ms) route={}", reqId, method, path, status, durationMs, routeId);
            }
        });
    }

    @Override
    public int getOrder() {
        return -1; // run early, before the CircuitBreaker/routing filters
    }
}
