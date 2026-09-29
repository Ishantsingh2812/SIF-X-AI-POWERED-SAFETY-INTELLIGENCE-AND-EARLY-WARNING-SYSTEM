package com.sih.sif.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RateLimitingFilter.java
 *
 * Implements a token-bucket rate limiter per client IP for POST /api/reports/analyze.
 * Limits clients to configurable requests/minute (default: 10).
 * Handles reverse proxy forwarding headers (X-Forwarded-For).
 * Cleans up stale IP buckets periodically to prevent memory exhaustion.
 */
@Component
@Order(1)
public class RateLimitingFilter extends OncePerRequestFilter {

    @Value("${app.rate-limit.analyze-per-minute:10}")
    private int requestsPerMinute;

    private static class TokenBucket {
        int tokens;
        long lastRefillTimestamp;

        TokenBucket(int maxTokens) {
            this.tokens = maxTokens;
            this.lastRefillTimestamp = System.currentTimeMillis();
        }

        synchronized boolean tryConsume(int maxTokens) {
            long now = System.currentTimeMillis();
            // Refill 1 minute window
            if (now - lastRefillTimestamp >= 60_000) {
                tokens = maxTokens;
                lastRefillTimestamp = now;
            }
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }

        synchronized boolean isStale() {
            return System.currentTimeMillis() - lastRefillTimestamp > 300_000; // 5 minutes inactivity
        }
    }

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private long lastCleanupTimestamp = System.currentTimeMillis();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if ("POST".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().endsWith("/api/reports/analyze")) {
            cleanupStaleBucketsIfNecessary();

            String clientIp = extractClientIp(request);
            TokenBucket bucket = buckets.computeIfAbsent(clientIp, k -> new TokenBucket(requestsPerMinute));

            if (!bucket.tryConsume(requestsPerMinute)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("Retry-After", "60");
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too Many Requests\",\"message\":\"Too many requests. Please wait a minute.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void cleanupStaleBucketsIfNecessary() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTimestamp > 60_000) {
            buckets.entrySet().removeIf(entry -> entry.getValue().isStale());
            lastCleanupTimestamp = now;
        }
    }
}
