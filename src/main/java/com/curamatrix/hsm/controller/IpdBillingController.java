package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.IpdChargeRequest;
import com.curamatrix.hsm.dto.request.RespondDiscountRequest;
import com.curamatrix.hsm.dto.request.SectionDiscountRequest;
import com.curamatrix.hsm.dto.request.IpdSettlementRequest;
import com.curamatrix.hsm.service.IpdBillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ipd")
@RequiredArgsConstructor
@Tag(name = "14. IPD Billing", description = "IPD running bill, charges, and discharge settlement")
public class IpdBillingController {

    private final IpdBillingService ipdBillingService;

    // ── Running Bill ──────────────────────────────────────────────────────────

    @GetMapping("/patient/{patientId}/running-bill")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    @Operation(summary = "Get running bill for a patient (unified OPD/IPD)")
    public ResponseEntity<Map<String, Object>> getRunningBill(@PathVariable Long patientId) {
        return ResponseEntity.ok(ipdBillingService.getRunningBill(patientId));
    }

    // ── Charges ───────────────────────────────────────────────────────────────

    @PostMapping("/patient/{patientId}/charges")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Add a manual charge to the running bill")
    public ResponseEntity<Map<String, Object>> addCharge(
            @PathVariable Long patientId,
            @Valid @RequestBody IpdChargeRequest request) {
        return ResponseEntity.ok(ipdBillingService.addCharge(patientId, request));
    }

    @PostMapping("/patient/{patientId}/charges/{itemId}/settle")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Settle a specific manual charge row")
    public ResponseEntity<Map<String, Object>> settleCharge(
            @PathVariable Long patientId,
            @PathVariable Long itemId,
            @RequestBody(required = false) Map<String, Object> body) {
        String paymentMethod = body != null && body.containsKey("paymentMethod") ? body.get("paymentMethod").toString() : "CASH";
        java.math.BigDecimal amountToPay = null;
        if (body != null && body.containsKey("amount")) {
            amountToPay = new java.math.BigDecimal(body.get("amount").toString());
        }
        return ResponseEntity.ok(ipdBillingService.settleChargeItem(patientId, itemId, paymentMethod, amountToPay));
    }

    @PostMapping("/patient/{patientId}/charges/settle-multiple")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Settle multiple specific manual charge rows")
    public ResponseEntity<Map<String, Object>> settleMultipleCharges(
            @PathVariable Long patientId,
            @RequestBody Map<String, Object> body) {
        List<Long> itemIds = ((List<?>) body.get("itemIds")).stream()
                .map(id -> Long.valueOf(id.toString()))
                .collect(Collectors.toList());
        String paymentMethod = body.containsKey("paymentMethod") ? body.get("paymentMethod").toString() : "CASH";
        return ResponseEntity.ok(ipdBillingService.settleChargeItems(patientId, itemIds, paymentMethod));
    }

    @DeleteMapping("/patient/{patientId}/charges/{itemId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Remove a manual charge from the running bill (before freeze)")
    public ResponseEntity<Map<String, Object>> removeCharge(
            @PathVariable Long patientId,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(ipdBillingService.removeCharge(patientId, itemId));
    }

    @PutMapping("/patient/{patientId}/charges/{itemId}/change-bed/{newBedId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Retroactively update the bed and daily rate of a past daily bed charge row")
    public ResponseEntity<Map<String, Object>> changeBedForChargeRow(
            @PathVariable Long patientId,
            @PathVariable Long itemId,
            @PathVariable Long newBedId) {
        return ResponseEntity.ok(ipdBillingService.changeBedForChargeRow(patientId, itemId, newBedId));
    }

    // ── Freeze ────────────────────────────────────────────────────────────────

    @PostMapping("/patient/{patientId}/freeze")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Freeze the bill — no more charges can be added after this")
    public ResponseEntity<Map<String, Object>> freezeBill(@PathVariable Long patientId) {
        return ResponseEntity.ok(ipdBillingService.freezeBill(patientId));
    }

    @PostMapping("/patient/{patientId}/unfreeze")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Unfreeze the bill — allows adding charges again")
    public ResponseEntity<Map<String, Object>> unfreezeBill(@PathVariable Long patientId) {
        return ResponseEntity.ok(ipdBillingService.unfreezeBill(patientId));
    }

    // ── Clear Discharge (Doctor action) ──────────────────────────────────────

    @PatchMapping("/patient/{patientId}/clear-discharge")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    @Operation(summary = "Doctor clears discharge — marks clinical work as done, unlocks Generate Invoice")
    public ResponseEntity<Map<String, Object>> clearDischarge(@PathVariable Long patientId) {
        return ResponseEntity.ok(ipdBillingService.clearDischarge(patientId));
    }

    // ── Generate Invoice ──────────────────────────────────────────────────────

    @PostMapping("/patient/{patientId}/generate-invoice")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Generate final invoice — freezes bill and marks invoiceGenerated=true, unlocks Discharge & Settle")
    public ResponseEntity<Map<String, Object>> generateInvoice(@PathVariable Long patientId) {
        return ResponseEntity.ok(ipdBillingService.generateInvoice(patientId));
    }

    // ── Final Bill ────────────────────────────────────────────────────────────

    @GetMapping("/patient/{patientId}/final-bill")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    @Operation(summary = "Get final bill breakdown for discharge")
    public ResponseEntity<Map<String, Object>> getFinalBill(@PathVariable Long patientId) {
        return ResponseEntity.ok(ipdBillingService.getFinalBill(patientId));
    }

    // ── Section Discounts ─────────────────────────────────────────────────────

    @PostMapping("/patient/{patientId}/section-discounts")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Apply or update a discount for a specific billing section")
    public ResponseEntity<Map<String, Object>> applySectionDiscount(
            @PathVariable Long patientId,
            @Valid @RequestBody SectionDiscountRequest request) {
        return ResponseEntity.ok(ipdBillingService.applySectionDiscount(patientId, request));
    }

    @GetMapping("/admins")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Get list of admins for targeting discount approvals")
    public ResponseEntity<List<Map<String, Object>>> getAdmins() {
        return ResponseEntity.ok(ipdBillingService.getAdmins());
    }

    @PostMapping("/patient/{patientId}/respond-discount")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve or reject pending discount for a patient's bill")
    public ResponseEntity<Map<String, Object>> respondDiscount(
            @PathVariable Long patientId,
            @Valid @RequestBody RespondDiscountRequest request) {
        return ResponseEntity.ok(ipdBillingService.respondDiscount(patientId, request));
    }

    // ── Settlement & Discharge ────────────────────────────────────────────────

    @PostMapping("/patient/{patientId}/final-settlement")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Collect final balance, close bill, release bed, and discharge patient")
    public ResponseEntity<Map<String, Object>> finalSettlement(
            @PathVariable Long patientId,
            @Valid @RequestBody IpdSettlementRequest request) {
        return ResponseEntity.ok(ipdBillingService.finalSettlement(patientId, request));
    }
}
