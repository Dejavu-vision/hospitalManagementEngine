package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.PreAuthRequestDto;
import com.curamatrix.hsm.dto.response.PreAuthResponseDto;
import com.curamatrix.hsm.service.PreAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pre-auths")
@RequiredArgsConstructor
@Tag(name = "14. TPA Pre-Authorisation", description = "Full TPA/Insurance pre-auth lifecycle: create, approve, enhance, query-response, final claim, settlement")
public class PreAuthController {

    private final PreAuthService preAuthService;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Operation(summary = "Create a new pre-auth request (DRAFT or SUBMITTED)")
    @PostMapping
    public ResponseEntity<PreAuthResponseDto> createPreAuth(@RequestBody PreAuthRequestDto request) {
        return ResponseEntity.ok(preAuthService.createPreAuth(request));
    }

    @Operation(summary = "Update pre-auth (add TPA reference, approved amount, coverage breakdown)")
    @PutMapping("/{id}")
    public ResponseEntity<PreAuthResponseDto> updatePreAuth(
            @PathVariable Long id,
            @RequestBody PreAuthRequestDto request) {
        return ResponseEntity.ok(preAuthService.updatePreAuth(id, request));
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Operation(summary = "List all pre-auths for this tenant (paginated)")
    @GetMapping
    public ResponseEntity<Page<PreAuthResponseDto>> getAllPreAuths(Pageable pageable) {
        return ResponseEntity.ok(preAuthService.getAllPreAuths(pageable));
    }

    @Operation(summary = "Get pre-auths by patient")
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<Page<PreAuthResponseDto>> getPreAuthsByPatient(
            @PathVariable Long patientId,
            Pageable pageable) {
        return ResponseEntity.ok(preAuthService.getPreAuthsByPatient(patientId, pageable));
    }

    @Operation(summary = "Get all pre-auths for an IPD admission (includes enhancements)")
    @GetMapping("/admission/{admissionId}")
    public ResponseEntity<List<PreAuthResponseDto>> getPreAuthsByAdmission(
            @PathVariable Long admissionId) {
        return ResponseEntity.ok(preAuthService.getPreAuthsByAdmission(admissionId));
    }

    // ── Workflow actions ──────────────────────────────────────────────────────

    @Operation(summary = "Create an enhancement pre-auth when initial approved amount is exhausted")
    @PostMapping("/{id}/enhancement")
    public ResponseEntity<PreAuthResponseDto> createEnhancement(
            @PathVariable Long id,
            @RequestBody PreAuthRequestDto request) {
        return ResponseEntity.ok(preAuthService.createEnhancement(id, request));
    }

    @Operation(summary = "Record hospital's response to a TPA query (moves status back to SUBMITTED)")
    @PatchMapping("/{id}/query-response")
    public ResponseEntity<PreAuthResponseDto> respondToQuery(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(preAuthService.respondToQuery(id, body.get("queryResponse")));
    }

    @Operation(summary = "Submit final claim to TPA at patient discharge")
    @PostMapping("/{id}/submit-claim")
    public ResponseEntity<PreAuthResponseDto> submitFinalClaim(
            @PathVariable Long id,
            @RequestBody Map<String, BigDecimal> body) {
        return ResponseEntity.ok(preAuthService.submitFinalClaim(id, body.get("finalClaimAmount")));
    }

    @Operation(summary = "Record TPA settlement — what the insurer actually paid")
    @PostMapping("/{id}/settle")
    public ResponseEntity<PreAuthResponseDto> settleClaim(
            @PathVariable Long id,
            @RequestBody Map<String, BigDecimal> body) {
        return ResponseEntity.ok(preAuthService.settleClaim(id, body.get("settledAmount")));
    }
}
