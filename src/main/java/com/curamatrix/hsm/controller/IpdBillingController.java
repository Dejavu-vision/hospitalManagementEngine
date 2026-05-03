package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.IpdChargeRequest;
import com.curamatrix.hsm.dto.request.IpdSettlementRequest;
import com.curamatrix.hsm.service.IpdBillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ipd")
@RequiredArgsConstructor
@Tag(name = "14. IPD Billing", description = "IPD running bill, charges, and discharge settlement")
public class IpdBillingController {

    private final IpdBillingService ipdBillingService;

    // ── Running Bill ──────────────────────────────────────────────────────────

    @GetMapping("/{admissionId}/running-bill")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    @Operation(summary = "Get running bill for an IPD admission")
    public ResponseEntity<Map<String, Object>> getRunningBill(@PathVariable Long admissionId) {
        return ResponseEntity.ok(ipdBillingService.getRunningBill(admissionId));
    }

    // ── Charges ───────────────────────────────────────────────────────────────

    @PostMapping("/{admissionId}/charges")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Add a manual charge to the running bill")
    public ResponseEntity<Map<String, Object>> addCharge(
            @PathVariable Long admissionId,
            @Valid @RequestBody IpdChargeRequest request) {
        return ResponseEntity.ok(ipdBillingService.addCharge(admissionId, request));
    }

    @DeleteMapping("/{admissionId}/charges/{itemId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Remove a manual charge from the running bill (before freeze)")
    public ResponseEntity<Map<String, Object>> removeCharge(
            @PathVariable Long admissionId,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(ipdBillingService.removeCharge(admissionId, itemId));
    }

    // ── Freeze ────────────────────────────────────────────────────────────────

    @PostMapping("/{admissionId}/freeze")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Freeze the bill — no more charges can be added after this")
    public ResponseEntity<Map<String, Object>> freezeBill(@PathVariable Long admissionId) {
        return ResponseEntity.ok(ipdBillingService.freezeBill(admissionId));
    }

    // ── Clear Discharge (Doctor action) ──────────────────────────────────────

    @PatchMapping("/{admissionId}/clear-discharge")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    @Operation(summary = "Doctor clears discharge — marks clinical work as done, unlocks Generate Invoice")
    public ResponseEntity<Map<String, Object>> clearDischarge(@PathVariable Long admissionId) {
        return ResponseEntity.ok(ipdBillingService.clearDischarge(admissionId));
    }

    // ── Generate Invoice ──────────────────────────────────────────────────────

    @PostMapping("/{admissionId}/generate-invoice")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Generate final invoice — freezes bill and marks invoiceGenerated=true, unlocks Discharge & Settle")
    public ResponseEntity<Map<String, Object>> generateInvoice(@PathVariable Long admissionId) {
        return ResponseEntity.ok(ipdBillingService.generateInvoice(admissionId));
    }

    // ── Final Bill ────────────────────────────────────────────────────────────

    @GetMapping("/{admissionId}/final-bill")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    @Operation(summary = "Get final bill breakdown for discharge")
    public ResponseEntity<Map<String, Object>> getFinalBill(@PathVariable Long admissionId) {
        return ResponseEntity.ok(ipdBillingService.getFinalBill(admissionId));
    }

    // ── Settlement & Discharge ────────────────────────────────────────────────

    @PostMapping("/{admissionId}/final-settlement")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Collect final balance, close bill, release bed, and discharge patient")
    public ResponseEntity<Map<String, Object>> finalSettlement(
            @PathVariable Long admissionId,
            @Valid @RequestBody IpdSettlementRequest request) {
        return ResponseEntity.ok(ipdBillingService.finalSettlement(admissionId, request));
    }
}
