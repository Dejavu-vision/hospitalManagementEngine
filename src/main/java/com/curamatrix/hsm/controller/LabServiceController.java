package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.LabServiceRequest;
import com.curamatrix.hsm.dto.request.PricingTierRequest;
import com.curamatrix.hsm.dto.response.LabServiceResponse;
import com.curamatrix.hsm.dto.response.LabServiceSearchResponse;
import com.curamatrix.hsm.dto.response.PricingTierResponse;
import com.curamatrix.hsm.enums.ServiceCategory;
import com.curamatrix.hsm.service.LabServiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/lab-services")
@RequiredArgsConstructor
public class LabServiceController {

    private final LabServiceService labServiceService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LabServiceResponse> createLabService(@Valid @RequestBody LabServiceRequest request) {
        log.info("Creating lab service: code={}", request.getServiceCode());
        LabServiceResponse response = labServiceService.createLabService(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LabServiceResponse> updateLabService(@PathVariable Long id,
                                                                @Valid @RequestBody LabServiceRequest request) {
        log.info("Updating lab service: id={}", id);
        LabServiceResponse response = labServiceService.updateLabService(id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LabServiceResponse> deactivateLabService(@PathVariable Long id) {
        log.info("Deactivating lab service: id={}", id);
        LabServiceResponse response = labServiceService.deactivateLabService(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<LabServiceResponse>> listLabServices(
            @RequestParam(required = false) ServiceCategory category,
            @RequestParam(required = false) Boolean active,
            Pageable pageable) {
        Page<LabServiceResponse> response = labServiceService.listLabServices(category, active, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<List<LabServiceSearchResponse>> searchLabServices(@RequestParam String q) {
        List<LabServiceSearchResponse> response = labServiceService.searchLabServices(q);
        return ResponseEntity.ok(response);
    }

    // ==================== Pricing Tier Endpoints ====================

    @PostMapping("/{id}/pricing-tiers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PricingTierResponse> addPricingTier(@PathVariable Long id,
                                                               @Valid @RequestBody PricingTierRequest request) {
        log.info("Adding pricing tier for lab service: id={}", id);
        PricingTierResponse response = labServiceService.addPricingTier(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{serviceId}/pricing-tiers/{tierId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PricingTierResponse> updatePricingTier(@PathVariable Long serviceId,
                                                                  @PathVariable Long tierId,
                                                                  @Valid @RequestBody PricingTierRequest request) {
        log.info("Updating pricing tier: tierId={}, serviceId={}", tierId, serviceId);
        PricingTierResponse response = labServiceService.updatePricingTier(serviceId, tierId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{serviceId}/pricing-tiers/{tierId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePricingTier(@PathVariable Long serviceId,
                                                   @PathVariable Long tierId) {
        log.info("Deleting pricing tier: tierId={}, serviceId={}", tierId, serviceId);
        labServiceService.deletePricingTier(serviceId, tierId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/pricing-tiers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PricingTierResponse>> listPricingTiers(@PathVariable Long id) {
        List<PricingTierResponse> response = labServiceService.listPricingTiers(id);
        return ResponseEntity.ok(response);
    }
}
