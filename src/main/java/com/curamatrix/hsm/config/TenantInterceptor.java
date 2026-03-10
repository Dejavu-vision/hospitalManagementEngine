package com.curamatrix.hsm.config;

import com.curamatrix.hsm.context.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.SecretKey;

/**
 * Intercepts all requests and sets tenant context from JWT token.
 */
@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    @Value("${jwt.secret:curamatrix-hsm-secret-key-for-jwt-token-generation-minimum-512-bits}")
    private String secret;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Skip tenant context for public endpoints
        String path = request.getRequestURI();
        if (isPublicEndpoint(path)) {
            log.debug("Skipping tenant context for public endpoint: {}", path);
            return true;
        }

        String token = extractToken(request);
        if (token != null) {
            try {
                Claims claims = extractClaims(token);
                Long tenantId = claims.get("tenantId", Long.class);
                String tenantKey = claims.get("tenantKey", String.class);

                if (tenantId != null) {
                    TenantContext.setTenantId(tenantId);
                    TenantContext.setTenantKey(tenantKey);
                    log.debug("Tenant context set from JWT: tenantId={}, tenantKey={}", tenantId, tenantKey);
                }
            } catch (Exception e) {
                log.error("Failed to extract tenant from token", e);
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                Object handler, Exception ex) {
        TenantContext.clear();
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private Claims extractClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/auth/") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/api/super-admin/tenants") && path.contains("register");
    }
}
