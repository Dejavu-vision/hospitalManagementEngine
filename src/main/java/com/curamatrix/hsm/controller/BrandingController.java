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

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Branding", description = "Dynamic hospital branding, logo, signature, and theme configuration")
public class BrandingController {

    private final TenantRepository tenantRepository;

    @PutMapping("/tenants/current/branding")
    @Transactional
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Update Current Hospital Branding & Theme", description = "Allows Hospital Admin or Super Admin to update their hospital's theme, logo, and identity")
    public ResponseEntity<Map<String, Object>> updateCurrentBranding(
            @RequestBody Map<String, Object> payload) {

        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No tenant context found for authenticated user"));
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found with ID: " + tenantId));

        // Update logo if provided
        if (payload.containsKey("logo")) {
            tenant.setLogo((String) payload.get("logo"));
        }

        // Update signature if provided
        if (payload.containsKey("signature")) {
            tenant.setSignature((String) payload.get("signature"));
        }

        // Update hospital name if provided
        if (payload.containsKey("hospitalName") && payload.get("hospitalName") != null) {
            String name = String.valueOf(payload.get("hospitalName")).trim();
            if (!name.isEmpty()) tenant.setHospitalName(name);
        } else if (payload.containsKey("name") && payload.get("name") != null) {
            String name = String.valueOf(payload.get("name")).trim();
            if (!name.isEmpty()) tenant.setHospitalName(name);
        }

        // Update address if provided
        if (payload.containsKey("address")) {
            tenant.setAddress((String) payload.get("address"));
        }

        // Update contactPhone if provided
        if (payload.containsKey("contactPhone")) {
            tenant.setContactPhone((String) payload.get("contactPhone"));
        } else if (payload.containsKey("phone")) {
            tenant.setContactPhone((String) payload.get("phone"));
        }

        // Update contactEmail if provided
        if (payload.containsKey("contactEmail")) {
            tenant.setContactEmail((String) payload.get("contactEmail"));
        } else if (payload.containsKey("email")) {
            tenant.setContactEmail((String) payload.get("email"));
        }

        // Merge settings
        Map<String, Object> currentSettings = tenant.getSettings() != null ? new HashMap<>(tenant.getSettings()) : new HashMap<>();
        if (payload.get("settings") instanceof Map<?, ?> incomingSettings) {
            for (Map.Entry<?, ?> entry : incomingSettings.entrySet()) {
                currentSettings.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }

        // Direct theme payload support
        if (payload.get("theme") instanceof Map<?, ?> incomingTheme) {
            currentSettings.put("theme", incomingTheme);
        }

        // Direct root color property support
        if (payload.containsKey("primaryColor") || payload.containsKey("accentColor") || payload.containsKey("preset")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> themeMap = currentSettings.get("theme") instanceof Map<?, ?>
                    ? new HashMap<>((Map<String, Object>) currentSettings.get("theme"))
                    : new HashMap<>();
            if (payload.containsKey("primaryColor")) themeMap.put("primaryColor", payload.get("primaryColor"));
            if (payload.containsKey("accentColor")) themeMap.put("accentColor", payload.get("accentColor"));
            if (payload.containsKey("preset")) themeMap.put("preset", payload.get("preset"));
            currentSettings.put("theme", themeMap);
        }

        // Identity settings fields
        for (String field : List.of("signatoryName", "signatoryTitle", "website", "accreditation", "emergencyPhone", "tagline")) {
            if (payload.containsKey(field)) {
                currentSettings.put(field, payload.get(field));
            }
        }

        tenant.setSettings(currentSettings);
        tenant = tenantRepository.save(tenant);
        log.info("Branding & theme updated for tenant: {}", tenant.getTenantKey());

        return ResponseEntity.ok(buildBrandingMap(tenant));
    }

    @GetMapping("/public/branding")
    @Operation(summary = "Get Public Hospital Branding", description = "Returns dynamic logo, signature, name, theme settings for a hospital by tenant key or ID")
    public ResponseEntity<Map<String, Object>> getPublicBranding(
            @RequestParam(required = false) String tenantKey,
            @RequestParam(required = false) Long tenantId,
            @RequestHeader(value = "X-Tenant-Key", required = false) String headerTenantKey,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId) {

        Long effectiveId = tenantId != null ? tenantId : headerTenantId;
        String key = tenantKey != null && !tenantKey.isBlank() ? tenantKey.trim() : headerTenantKey;
        Optional<Tenant> tenantOpt = Optional.empty();

        if (effectiveId != null) {
            tenantOpt = tenantRepository.findById(effectiveId);
        }

        if (tenantOpt.isEmpty() && key != null && !key.isBlank()) {
            tenantOpt = tenantRepository.findByTenantKey(key);
        }

        if (tenantOpt.isEmpty()) {
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
    public ResponseEntity<Map<String, Object>> getCurrentBranding(
            @RequestHeader(value = "X-Tenant-Key", required = false) String headerTenantKey,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId) {

        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null && headerTenantId != null) {
            tenantId = headerTenantId;
        }

        if (tenantId != null) {
            return tenantRepository.findById(tenantId)
                    .map(this::buildBrandingMap)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.ok(buildFallbackBranding(null)));
        }

        String key = TenantContext.getTenantKey() != null ? TenantContext.getTenantKey() : headerTenantKey;
        if (key != null && !key.isBlank()) {
            return tenantRepository.findByTenantKey(key)
                    .map(this::buildBrandingMap)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.ok(buildFallbackBranding(key)));
        }

        return getPublicBranding(null, null, null, null);
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
