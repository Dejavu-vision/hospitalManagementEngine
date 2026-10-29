package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.entity.Tenant;
import com.curamatrix.hsm.repository.TenantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Branding", description = "Dynamic hospital branding, logo, signature, and theme configuration")
public class BrandingController {

    private final TenantRepository tenantRepository;

    @GetMapping("/public/branding")
    @Operation(summary = "Get Public Hospital Branding", description = "Returns dynamic logo, signature, name, theme settings for a hospital by tenant key")
    public ResponseEntity<Map<String, Object>> getPublicBranding(
            @RequestParam(required = false) String tenantKey,
            @RequestHeader(value = "X-Tenant-Key", required = false) String headerTenantKey) {

        String key = tenantKey != null && !tenantKey.isBlank() ? tenantKey.trim() : headerTenantKey;
        Optional<Tenant> tenantOpt;

        if (key != null && !key.isBlank()) {
            tenantOpt = tenantRepository.findByTenantKey(key);
        } else {
            // Default to first active tenant or default-hospital
            tenantOpt = tenantRepository.findByTenantKey("default-hospital");
            if (tenantOpt.isEmpty()) {
                tenantOpt = tenantRepository.findAll().stream().filter(t -> Boolean.TRUE.equals(t.getIsActive())).findFirst();
            }
        }

        return tenantOpt.map(this::buildBrandingMap)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(buildFallbackBranding(key)));
    }

    @GetMapping("/tenants/current/branding")
    @Operation(summary = "Get Current Hospital Branding", description = "Returns dynamic branding for currently authenticated tenant")
    public ResponseEntity<Map<String, Object>> getCurrentBranding() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return getPublicBranding(null, null);
        }

        return tenantRepository.findById(tenantId)
                .map(this::buildBrandingMap)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(buildFallbackBranding(null)));
    }

    private Map<String, Object> buildBrandingMap(Tenant tenant) {
        Map<String, Object> map = new HashMap<>();
        map.put("tenantId", tenant.getId());
        map.put("tenantKey", tenant.getTenantKey());
        map.put("hospitalName", tenant.getHospitalName());
        map.put("logo", tenant.getLogo());
        map.put("signature", tenant.getSignature());
        map.put("address", tenant.getAddress());
        map.put("contactPhone", tenant.getContactPhone());
        map.put("contactEmail", tenant.getContactEmail());
        map.put("subscriptionPlan", tenant.getSubscriptionPlan());
        map.put("settings", tenant.getSettings() != null ? tenant.getSettings() : new HashMap<>());
        return map;
    }

    private Map<String, Object> buildFallbackBranding(String key) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("tenantKey", key != null ? key : "default-hospital");
        fallback.put("hospitalName", "CuraMatrix Multi-Speciality Hospital");
        fallback.put("logo", null);
        fallback.put("signature", null);
        fallback.put("address", "Bengaluru, Karnataka");
        fallback.put("contactPhone", "080-2630 4050");
        fallback.put("contactEmail", "patientcare@curamatrix.com");
        fallback.put("settings", new HashMap<>());
        return fallback;
    }
}
