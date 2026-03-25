package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.TenantRegistrationRequest;
import com.curamatrix.hsm.dto.request.AdminPasswordResetRequest;
import com.curamatrix.hsm.dto.response.TenantResponse;
import com.curamatrix.hsm.service.TenantManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/super-admin/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin - Tenant Management", description = "Manage hospital tenants (Super Admin only)")
public class SuperAdminController {

    private final TenantManagementService tenantManagementService;

    @PostMapping
    public ResponseEntity<TenantResponse> registerTenant(@Valid @RequestBody TenantRegistrationRequest request) {
        log.info("Registering new tenant: {}", request.getTenantKey());
        TenantResponse response = tenantManagementService.registerTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List Tenants", description = "Paginated and filterable tenant listing. " +
            "Supports filtering by active status and subscription plan.")
    public ResponseEntity<Page<TenantResponse>> getAllTenants(
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String plan,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<TenantResponse> tenants = tenantManagementService.getTenantsPaginated(isActive, plan, pageable);
        return ResponseEntity.ok(tenants);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> getTenantById(@PathVariable Long id) {
        TenantResponse tenant = tenantManagementService.getTenantById(id);
        return ResponseEntity.ok(tenant);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TenantResponse> updateTenant(
            @PathVariable Long id,
            @Valid @RequestBody TenantRegistrationRequest request) {
        log.info("Updating tenant: {}", id);
        TenantResponse response = tenantManagementService.updateTenant(id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/suspend")
    public ResponseEntity<Void> suspendTenant(@PathVariable Long id) {
        log.info("Suspending tenant: {}", id);
        tenantManagementService.suspendTenant(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<Void> activateTenant(@PathVariable Long id) {
        log.info("Activating tenant: {}", id);
        tenantManagementService.activateTenant(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<Object> getTenantStats(@PathVariable Long id) {
        Object stats = tenantManagementService.getTenantStats(id);
        return ResponseEntity.ok(stats);
    }

    @PutMapping("/{id}/admin/password")
    public ResponseEntity<Void> resetTenantAdminPassword(
            @PathVariable Long id,
            @Valid @RequestBody AdminPasswordResetRequest request) {
        log.info("Resetting admin password for tenant: {}", id);
        tenantManagementService.resetTenantAdminPassword(id, request);
        return ResponseEntity.noContent().build();
    }
}
