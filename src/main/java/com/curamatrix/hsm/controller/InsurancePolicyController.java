package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.InsurancePolicyRequest;
import com.curamatrix.hsm.dto.response.InsurancePolicyResponse;
import com.curamatrix.hsm.service.InsurancePolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/insurance-policies")
@RequiredArgsConstructor
@Tag(name = "Insurance Policies", description = "Manage patient insurance policies (TPA Desk)")
public class InsurancePolicyController {

    private final InsurancePolicyService policyService;

    @Operation(summary = "Get all policies for a patient")
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<InsurancePolicyResponse>> getPoliciesByPatient(@PathVariable Long patientId) {
        return ResponseEntity.ok(policyService.getPoliciesByPatient(patientId));
    }

    @Operation(summary = "Create a new policy for a patient (TPA desk)")
    @PostMapping("/patient/{patientId}")
    public ResponseEntity<InsurancePolicyResponse> createPolicy(
            @PathVariable Long patientId,
            @RequestBody InsurancePolicyRequest request) {
        return ResponseEntity.ok(policyService.createPolicy(patientId, request));
    }

    @Operation(summary = "Update full policy details (TPA desk)")
    @PutMapping("/{id}")
    public ResponseEntity<InsurancePolicyResponse> updatePolicy(
            @PathVariable Long id,
            @RequestBody InsurancePolicyRequest request) {
        return ResponseEntity.ok(policyService.updatePolicy(id, request));
    }
}
