package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.AdmissionRequest;
import com.curamatrix.hsm.dto.request.BedTransferRequest;
import com.curamatrix.hsm.dto.response.AdmissionResponse;
import com.curamatrix.hsm.service.IpdAdmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.service.BedChargeEngine;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admissions")
@RequiredArgsConstructor
@Tag(name = "12. IPD Admissions", description = "Manage Inpatient Admissions and Discharges")
public class IpdAdmissionController {

    private final IpdAdmissionService admissionService;
    private final BedChargeEngine bedChargeEngine;

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    @Operation(summary = "Admit a patient (Emergency, OPD Convert, or Direct)")
    public ResponseEntity<AdmissionResponse> admitPatient(@Valid @RequestBody AdmissionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(admissionService.admitPatient(request));
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<AdmissionResponse> getAdmission(@PathVariable Long id) {
        return ResponseEntity.ok(admissionService.getAdmission(id));
    }

    @GetMapping("/by-bed/{bedId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN', 'NURSE')")
    @Operation(summary = "Get current admission by active bed allocation")
    public ResponseEntity<AdmissionResponse> getAdmissionByBed(@PathVariable Long bedId) {
        return ResponseEntity.ok(admissionService.getAdmissionByBedId(bedId));
    }

    @PostMapping("/{id}/discharge")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(summary = "Initiate clinical discharge, freeing the bed and locking the episode")
    public ResponseEntity<AdmissionResponse> dischargePatient(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> payload) {
        String summary = payload != null ? payload.get("dischargeSummary") : null;
        return ResponseEntity.ok(admissionService.dischargePatient(id, summary));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    @Operation(summary = "List all currently admitted inpatients with bed and billing info")
    public ResponseEntity<List<Map<String, Object>>> getActiveAdmissions() {
        return ResponseEntity.ok(admissionService.getActiveAdmissions());
    }

    @GetMapping("/{id}/billing")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    @Operation(summary = "Get the running bill for a specific IPD admission")
    public ResponseEntity<Map<String, Object>> getAdmissionBilling(@PathVariable Long id) {
        return ResponseEntity.ok(admissionService.getAdmissionBilling(id));
    }

    @PostMapping("/{id}/transfer-bed/{newBedId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    @Operation(summary = "Transfer patient to a different bed/ward")
    public ResponseEntity<Void> transferBed(
            @PathVariable Long id, 
            @PathVariable Long newBedId,
            @RequestBody(required = false) BedTransferRequest request) {
        String reason = request != null ? request.getTransferReason() : null;
        admissionService.transferBed(id, newBedId, reason);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/test/trigger-bed-charges")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ad-hoc trigger for testing midnight bed charges sweep")
    public ResponseEntity<String> triggerBedCharges() {
        int count = bedChargeEngine.triggerManualSweep(TenantContext.getTenantId());
        return ResponseEntity.ok("Successfully applied bed charges to " + count + " active IPD allocations.");
    }
}
