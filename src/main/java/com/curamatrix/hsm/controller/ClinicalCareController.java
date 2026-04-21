package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.AddProgressNoteRequest;
import com.curamatrix.hsm.dto.request.AddVitalSignRequest;
import com.curamatrix.hsm.dto.response.DailyProgressNoteResponse;
import com.curamatrix.hsm.dto.response.VitalSignResponse;
import com.curamatrix.hsm.service.ClinicalCareService;
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
@RequestMapping("/api/ipd/care")
@RequiredArgsConstructor
@Tag(name = "13. IPD Clinical Care", description = "Vitals and Doctor SOAP Notes for admitted patients")
public class ClinicalCareController {

    private final ClinicalCareService clinicalCareService;

    // ─── VITALS ──────────────────────────────────────────────────────────────

    @PostMapping("/{admissionId}/vitals")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Add a new basic vital sign reading to the IPD running episode")
    public ResponseEntity<VitalSignResponse> addVitalSign(
            @PathVariable Long admissionId,
            @Valid @RequestBody AddVitalSignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalCareService.addVitalSign(admissionId, request));
    }

    @GetMapping("/{admissionId}/vitals")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Get sequential flow sheet of vitals for this admission")
    public ResponseEntity<List<VitalSignResponse>> getVitals(
            @PathVariable Long admissionId) {
        return ResponseEntity.ok(clinicalCareService.getVitalSigns(admissionId));
    }

    // ─── DOCTOR NOTES ────────────────────────────────────────────────────────

    @PostMapping("/{admissionId}/progress-notes")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(summary = "Add a daily SOAP format progress note by the consulting doctor")
    public ResponseEntity<DailyProgressNoteResponse> addProgressNote(
            @PathVariable Long admissionId,
            @Valid @RequestBody AddProgressNoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalCareService.addProgressNote(admissionId, request));
    }

    @GetMapping("/{admissionId}/progress-notes")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Get all daily progress notes for this admission")
    public ResponseEntity<List<DailyProgressNoteResponse>> getProgressNotes(
            @PathVariable Long admissionId) {
        return ResponseEntity.ok(clinicalCareService.getProgressNotes(admissionId));
    }
}
