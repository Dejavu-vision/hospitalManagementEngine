package com.curamatrix.hsm.context;

import lombok.extern.slf4j.Slf4j;

/**
 * Thread-local storage for current tenant context.
 * Automatically managed by TenantInterceptor.
 */
@Slf4j
public class TenantContext {

    private static final ThreadLocal<Long> currentTenant = new ThreadLocal<>();
    private static final ThreadLocal<String> currentTenantKey = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        currentTenant.set(tenantId);
        log.debug("Tenant context set: tenantId={}", tenantId);
    }

    public static Long getTenantId() {
        return currentTenant.get();
    }

    public static void setTenantKey(String tenantKey) {
        currentTenantKey.set(tenantKey);
    }

    public static String getTenantKey() {
        return currentTenantKey.get();
    }

    public static void clear() {
        Long tenantId = currentTenant.get();
        log.debug("Clearing tenant context: tenantId={}", tenantId);
        currentTenant.remove();
        currentTenantKey.remove();
    }

    public static boolean isSet() {
        return currentTenant.get() != null;
    }
}
