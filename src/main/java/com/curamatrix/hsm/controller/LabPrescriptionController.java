package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.LabPrescriptionRequest;
import com.curamatrix.hsm.dto.request.ReceptionistLabRegistrationRequest;
import com.curamatrix.hsm.dto.response.LabPrescriptionResponse;
import com.curamatrix.hsm.service.LabPrescriptionService;
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
@RequestMapping("/api/lab-prescriptions")
@RequiredArgsConstructor
public class LabPrescriptionController {

    private final LabPrescriptionService labPrescriptionService;

    @PostMapping("/doctor")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<LabPrescriptionResponse> createDoctorPrescription(
            @Valid @RequestBody LabPrescriptionRequest request) {
        log.info("Creating doctor lab prescription for patient: {}", request.getPatientId());
        LabPrescriptionResponse response = labPrescriptionService.createDoctorPrescription(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/receptionist")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<LabPrescriptionResponse> createReceptionistRegistration(
            @Valid @RequestBody ReceptionistLabRegistrationRequest request) {
        log.info("Creating receptionist lab registration for patient: {}", request.getPatientId());
        LabPrescriptionResponse response = labPrescriptionService.createReceptionistRegistration(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<List<LabPrescriptionResponse>> getPatientPrescriptions(@PathVariable Long patientId) {
        List<LabPrescriptionResponse> response = labPrescriptionService.getPrescriptionsByPatient(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN', 'LAB_STAFF')")
    public ResponseEntity<LabPrescriptionResponse> getPrescriptionDetails(@PathVariable Long id) {
        LabPrescriptionResponse response = labPrescriptionService.getPrescriptionById(id);
        return ResponseEntity.ok(response);
    }
}
