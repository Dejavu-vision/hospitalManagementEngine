package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.AppointmentRequest;
import com.curamatrix.hsm.dto.request.WalkInRequest;
import com.curamatrix.hsm.dto.response.AppointmentResponse;
import com.curamatrix.hsm.dto.response.SlotResponse;
import com.curamatrix.hsm.enums.AppointmentStatus;
import com.curamatrix.hsm.enums.AppointmentType;
import com.curamatrix.hsm.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "4. Appointments", description = "Appointment booking and queue management")
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<AppointmentResponse> bookAppointment(@Valid @RequestBody AppointmentRequest request) {
        log.info("Booking appointment for patient: {}", request.getPatientId());
        AppointmentResponse response = appointmentService.bookAppointment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/walk-in")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<AppointmentResponse> createWalkIn(@Valid @RequestBody WalkInRequest request) {
        log.info("Creating walk-in appointment for patient: {}", request.getPatientId());
        AppointmentResponse response = appointmentService.createWalkIn(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<Page<AppointmentResponse>> getAppointments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) AppointmentType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<AppointmentResponse> appointments = appointmentService.getAppointments(
                date, doctorId, patientId, status, type, pageable);
        return ResponseEntity.ok(appointments);
    }

    @GetMapping("/doctor/{doctorId}/slots")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<SlotResponse> getAvailableSlots(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        SlotResponse response = appointmentService.getAvailableSlots(doctorId, date);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/doctor/{doctorId}/today")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<AppointmentResponse>> getTodayQueue(@PathVariable Long doctorId) {
        List<AppointmentResponse> queue = appointmentService.getTodayQueue(doctorId);
        return ResponseEntity.ok(queue);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<AppointmentResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam AppointmentStatus status) {
        log.info("Updating appointment {} status to {}", id, status);
        AppointmentResponse response = appointmentService.updateStatus(id, status);
        return ResponseEntity.ok(response);
    }
}
