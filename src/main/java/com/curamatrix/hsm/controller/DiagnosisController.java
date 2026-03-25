package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.DiagnosisRequest;
import com.curamatrix.hsm.dto.response.DiagnosisResponse;
import com.curamatrix.hsm.service.DiagnosisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/diagnoses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR')")
@io.swagger.v3.oas.annotations.tags.Tag(name = "5. Diagnosis", description = "Patient diagnosis management (Doctor only)")
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    @PostMapping
    public ResponseEntity<DiagnosisResponse> createDiagnosis(@Valid @RequestBody DiagnosisRequest request) {
        log.info("Creating diagnosis for appointment: {}", request.getAppointmentId());
        DiagnosisResponse response = diagnosisService.createDiagnosis(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DiagnosisResponse> getDiagnosis(@PathVariable Long id) {
        DiagnosisResponse response = diagnosisService.getDiagnosisById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<DiagnosisResponse> getDiagnosisByAppointment(@PathVariable Long appointmentId) {
        DiagnosisResponse response = diagnosisService.getDiagnosisByAppointmentId(appointmentId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DiagnosisResponse> updateDiagnosis(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosisRequest request) {
        log.info("Updating diagnosis: {}", id);
        DiagnosisResponse response = diagnosisService.updateDiagnosis(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<DiagnosisResponse>> getPatientDiagnosisHistory(@PathVariable Long patientId) {
        List<DiagnosisResponse> responses = diagnosisService.getDiagnosesByPatientId(patientId);
        return ResponseEntity.ok(responses);
    }
}
