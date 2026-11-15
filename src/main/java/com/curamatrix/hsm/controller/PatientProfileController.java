package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.PatientAllergyRequest;
import com.curamatrix.hsm.dto.request.PatientNoteRequest;
import com.curamatrix.hsm.dto.request.PatientVitalRequest;
import com.curamatrix.hsm.dto.response.PatientAllergyResponse;
import com.curamatrix.hsm.dto.response.PatientNoteResponse;
import com.curamatrix.hsm.dto.response.PatientProfileResponse;
import com.curamatrix.hsm.dto.response.PatientVisitDetailResponse;
import com.curamatrix.hsm.dto.response.PatientVitalResponse;
import com.curamatrix.hsm.service.PatientAccessAuditService;
import com.curamatrix.hsm.service.PatientClinicalService;
import com.curamatrix.hsm.service.PatientProfileService;
import com.curamatrix.hsm.service.PatientVisitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Unified patient profile plus structured allergies, vitals and notes.
 * Used by receptionists, doctors and admins; content is trimmed by role in the service.
 */
@RestController
@RequestMapping("/api/patients/{patientId}")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
public class PatientProfileController {

    private final PatientProfileService profileService;
    private final PatientClinicalService clinicalService;
    private final PatientAccessAuditService auditService;
    private final PatientVisitService visitService;

    @GetMapping("/profile")
    public ResponseEntity<PatientProfileResponse> getProfile(@PathVariable Long patientId) {
        PatientProfileResponse profile = profileService.getProfile(patientId);
        auditService.record(patientId, "VIEW_PROFILE");
        return ResponseEntity.ok(profile);
    }

    /** Full detail of the most recent visit. 204 when the patient has no visit yet. */
    @GetMapping("/visits/last")
    public ResponseEntity<PatientVisitDetailResponse> getLastVisit(@PathVariable Long patientId) {
        return visitService.getLastVisit(patientId)
                .map(v -> {
                    auditService.record(patientId, "VIEW_VISIT");
                    return ResponseEntity.ok(v);
                })
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Full detail of one specific visit. */
    @GetMapping("/visits/{appointmentId}")
    public ResponseEntity<PatientVisitDetailResponse> getVisit(@PathVariable Long patientId,
                                                               @PathVariable Long appointmentId) {
        PatientVisitDetailResponse v = visitService.getVisit(patientId, appointmentId);
        auditService.record(patientId, "VIEW_VISIT");
        return ResponseEntity.ok(v);
    }

    // Allergies
    @GetMapping("/allergies")
    public ResponseEntity<List<PatientAllergyResponse>> listAllergies(@PathVariable Long patientId) {
        return ResponseEntity.ok(clinicalService.listAllergies(patientId));
    }

    @PostMapping("/allergies")
    public ResponseEntity<PatientAllergyResponse> addAllergy(@PathVariable Long patientId,
                                                             @Valid @RequestBody PatientAllergyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.addAllergy(patientId, request));
    }

    @DeleteMapping("/allergies/{allergyId}")
    public ResponseEntity<Void> removeAllergy(@PathVariable Long patientId, @PathVariable Long allergyId) {
        clinicalService.removeAllergy(patientId, allergyId);
        return ResponseEntity.noContent().build();
    }

    // Vitals
    @GetMapping("/vitals")
    public ResponseEntity<List<PatientVitalResponse>> listVitals(@PathVariable Long patientId,
                                                                 @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(clinicalService.listVitals(patientId, limit));
    }

    @PostMapping("/vitals")
    public ResponseEntity<PatientVitalResponse> addVitals(@PathVariable Long patientId,
                                                          @Valid @RequestBody PatientVitalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.addVitals(patientId, request));
    }

    // Notes
    @GetMapping("/notes")
    public ResponseEntity<List<PatientNoteResponse>> listNotes(@PathVariable Long patientId) {
        return ResponseEntity.ok(clinicalService.listNotes(patientId));
    }

    @PostMapping("/notes")
    public ResponseEntity<PatientNoteResponse> addNote(@PathVariable Long patientId,
                                                       @Valid @RequestBody PatientNoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.addNote(patientId, request));
    }

    @DeleteMapping("/notes/{noteId}")
    public ResponseEntity<Void> deleteNote(@PathVariable Long patientId, @PathVariable Long noteId) {
        clinicalService.deleteNote(patientId, noteId);
        return ResponseEntity.noContent().build();
    }
}
