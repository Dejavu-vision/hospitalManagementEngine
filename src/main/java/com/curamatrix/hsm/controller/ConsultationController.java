package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.ConsultationSubmitRequest;
import com.curamatrix.hsm.dto.response.AppointmentResponse;
import com.curamatrix.hsm.dto.response.ConsultationResponse;
import com.curamatrix.hsm.dto.response.PrintPreviewResponse;
import com.curamatrix.hsm.dto.response.QueueEntryResponse;
import com.curamatrix.hsm.entity.Doctor;
import com.curamatrix.hsm.service.ConsultationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/consultations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR')")
@io.swagger.v3.oas.annotations.tags.Tag(name = "6. Consultations", description = "Doctor consultation workflow management")
public class ConsultationController {

    private final ConsultationService consultationService;

    @PostMapping("/{appointmentId}/confirm-arrival")
    public ResponseEntity<AppointmentResponse> confirmArrival(@PathVariable Long appointmentId) {
        log.info("Confirming arrival for appointment: {}", appointmentId);
        AppointmentResponse response = consultationService.confirmArrival(appointmentId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/submit")
    public ResponseEntity<ConsultationResponse> submitConsultation(
            @Valid @RequestBody ConsultationSubmitRequest request) {
        log.info("Submitting consultation for appointment: {}", request.getAppointmentId());
        ConsultationResponse response = consultationService.submitConsultation(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{appointmentId}/print")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<PrintPreviewResponse> getPrintPreview(@PathVariable Long appointmentId) {
        log.info("Fetching print preview for appointment: {}", appointmentId);
        PrintPreviewResponse response = consultationService.getConsultationForPrint(appointmentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-queue")
    public ResponseEntity<List<QueueEntryResponse>> getMyQueue() {
        Doctor doctor = consultationService.getAuthenticatedDoctor();
        log.info("Fetching queue for doctor: {}", doctor.getId());
        List<QueueEntryResponse> queue = consultationService.getDoctorQueue(doctor.getId());
        return ResponseEntity.ok(queue);
    }

    @GetMapping("/my-patients")
    public ResponseEntity<List<com.curamatrix.hsm.dto.response.PatientResponse>> getMyPatients() {
        Doctor doctor = consultationService.getAuthenticatedDoctor();
        log.info("Fetching patients for doctor: {}", doctor.getId());
        List<com.curamatrix.hsm.dto.response.PatientResponse> patients = consultationService.getDoctorPatients(doctor.getId());
        return ResponseEntity.ok(patients);
    }
}
