package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.PrescriptionBatchRequest;
import com.curamatrix.hsm.dto.response.PrescriptionResponse;
import com.curamatrix.hsm.service.PrescriptionService;
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
@RequestMapping("/api/prescriptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR')")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    @PostMapping
    public ResponseEntity<List<PrescriptionResponse>> addPrescriptions(
            @Valid @RequestBody PrescriptionBatchRequest request) {
        log.info("Adding prescriptions for diagnosis: {}", request.getDiagnosisId());
        List<PrescriptionResponse> responses = prescriptionService.addPrescriptions(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @GetMapping("/diagnosis/{diagnosisId}")
    public ResponseEntity<List<PrescriptionResponse>> getPrescriptionsByDiagnosis(@PathVariable Long diagnosisId) {
        List<PrescriptionResponse> responses = prescriptionService.getPrescriptionsByDiagnosisId(diagnosisId);
        return ResponseEntity.ok(responses);
    }
}
