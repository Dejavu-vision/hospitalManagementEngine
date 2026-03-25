package com.curamatrix.hsm.config;

import com.curamatrix.hsm.enums.SubscriptionPlan;
import com.curamatrix.hsm.repository.TenantRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-tenant API rate limiting filter using a sliding window counter pattern.
 * Rates are sourced from the tenant's subscription plan:
 *   BASIC:    1,000 API calls/hour
 *   STANDARD: 10,000 API calls/hour
 *   PREMIUM:  Unlimited
 *
 * Returns HTTP 429 when the limit is exceeded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TenantRepository tenantRepository;

    private static final long WINDOW_MILLIS = 60 * 60 * 1000L; // 1 hour

    private final ConcurrentHashMap<Long, RateBucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Only rate-limit authenticated API requests
        String bearerToken = request.getHeader("Authorization");
        if (!StringUtils.hasText(bearerToken) || !bearerToken.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String jwt = bearerToken.substring(7);
            Claims claims = jwtUtil.getAllClaims(jwt);
            Object tenantIdObj = claims.get("tenantId");

            if (tenantIdObj == null) {
                filterChain.doFilter(request, response);
                return;
            }

            Long tenantId = Long.valueOf(tenantIdObj.toString());

            // Look up the tenant's plan to determine the rate limit
            int maxCallsPerHour = getMaxCallsForTenant(tenantId);

            // Unlimited plan — skip rate limiting
            if (maxCallsPerHour < 0) {
                filterChain.doFilter(request, response);
                return;
            }

            // Check and increment the counter
            RateBucket bucket = buckets.computeIfAbsent(tenantId, k -> new RateBucket());
            if (!bucket.tryConsume(maxCallsPerHour)) {
                log.warn("Rate limit exceeded for tenant: {} (limit: {}/hr)", tenantId, maxCallsPerHour);
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"status\":429,\"error\":\"Too Many Requests\"," +
                        "\"message\":\"API rate limit exceeded. Your plan allows " + maxCallsPerHour +
                        " calls per hour. Please upgrade your subscription or try again later.\"}");
                return;
            }

        } catch (Exception e) {
            // If rate limiting logic fails, don't block the request — just log
            log.debug("Rate limiting skipped due to error: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private int getMaxCallsForTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .map(tenant -> {
                    try {
                        SubscriptionPlan plan = SubscriptionPlan.valueOf(tenant.getSubscriptionPlan());
                        return plan.getApiCallsPerHour();
                    } catch (IllegalArgumentException e) {
                        return 1000; // Default to BASIC limit
                    }
                })
                .orElse(1000); // Default to BASIC limit
    }

    /**
     * Simple sliding window rate bucket using atomic operations.
     * Thread-safe for concurrent access.
     */
    private static class RateBucket {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final AtomicLong windowStart = new AtomicLong(Instant.now().toEpochMilli());

        boolean tryConsume(int maxCalls) {
            long now = Instant.now().toEpochMilli();
            long start = windowStart.get();

            // Reset if window has expired
            if (now - start > WINDOW_MILLIS) {
                windowStart.set(now);
                counter.set(1);
                return true;
            }

            int current = counter.incrementAndGet();
            return current <= maxCalls;
        }
    }
}
