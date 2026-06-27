package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.HospitalServiceRequest;
import com.curamatrix.hsm.dto.response.HospitalServiceResponse;
import com.curamatrix.hsm.enums.BillingItemType;
import com.curamatrix.hsm.service.HospitalServiceCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/service-catalog")
@RequiredArgsConstructor
@Tag(name = "Hospital Service Catalog", description = "Admin endpoints for managing hospital services and rates")
public class HospitalServiceController {

    private final HospitalServiceCatalogService catalogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @Operation(summary = "Get all hospital services with optional filters")
    public ResponseEntity<List<HospitalServiceResponse>> getAllServices(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) BillingItemType type,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(catalogService.getAllServices(departmentId, type, active));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @Operation(summary = "Get a hospital service by ID")
    public ResponseEntity<HospitalServiceResponse> getServiceById(@PathVariable Long id) {
        return ResponseEntity.ok(catalogService.getServiceById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new hospital service")
    public ResponseEntity<HospitalServiceResponse> createService(@Valid @RequestBody HospitalServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.createService(request));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Bulk create or update hospital services")
    public ResponseEntity<List<HospitalServiceResponse>> bulkSaveServices(
            @Valid @RequestBody List<HospitalServiceRequest> requests) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.bulkSaveServices(requests));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an existing hospital service")
    public ResponseEntity<HospitalServiceResponse> updateService(
            @PathVariable Long id,
            @Valid @RequestBody HospitalServiceRequest request) {
        return ResponseEntity.ok(catalogService.updateService(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Soft delete a hospital service")
    public ResponseEntity<Void> deleteService(@PathVariable Long id) {
        catalogService.deleteService(id);
        return ResponseEntity.noContent().build();
    }
}
