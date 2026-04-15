package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.DoctorAvailabilityRequest;
import com.curamatrix.hsm.dto.request.DoctorStatusUpdateRequest;
import com.curamatrix.hsm.dto.response.DoctorAvailabilityResponse;
import com.curamatrix.hsm.service.DoctorAvailabilityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "6. Doctor Availability", description = "Doctor daily presence and real-time status management")
public class DoctorAvailabilityController {

    private final DoctorAvailabilityService availabilityService;

    @GetMapping("/api/doctors/availability/today")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN', 'DOCTOR')")
    public ResponseEntity<List<DoctorAvailabilityResponse>> getTodayAvailability() {
        return ResponseEntity.ok(availabilityService.getTodayAvailability());
    }

    @GetMapping("/api/doctors/me/availability")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DoctorAvailabilityResponse> getMyAvailability(Authentication auth) {
        // Principal name is the email (see JwtAuthenticationFilter)
        return ResponseEntity.ok(availabilityService.getAvailabilityByEmail(auth.getName()));
    }

    @GetMapping("/api/doctors/{doctorId}/availability")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN', 'DOCTOR')")
    public ResponseEntity<DoctorAvailabilityResponse> getAvailability(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(availabilityService.getAvailability(doctorId, date));
    }

    @PostMapping("/api/doctors/{doctorId}/availability")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<DoctorAvailabilityResponse> createAvailability(
            @PathVariable Long doctorId,
            @Valid @RequestBody DoctorAvailabilityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(availabilityService.upsertAvailability(doctorId, request));
    }

    @PutMapping("/api/doctors/{doctorId}/availability")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<DoctorAvailabilityResponse> updateAvailability(
            @PathVariable Long doctorId,
            @Valid @RequestBody DoctorAvailabilityRequest request) {
        return ResponseEntity.ok(availabilityService.upsertAvailability(doctorId, request));
    }

    @PatchMapping("/api/doctors/{doctorId}/status")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN', 'DOCTOR')")
    public ResponseEntity<DoctorAvailabilityResponse> updateStatus(
            @PathVariable Long doctorId,
            @Valid @RequestBody DoctorStatusUpdateRequest request) {
        return ResponseEntity.ok(availabilityService.updateStatus(doctorId, request));
    }
}
