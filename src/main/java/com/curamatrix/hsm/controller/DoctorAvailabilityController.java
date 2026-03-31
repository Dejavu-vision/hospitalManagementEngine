package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.DoctorAvailabilityRequest;
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
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/doctors/{doctorId}/availability")
@RequiredArgsConstructor
@Tag(name = "6. Doctor Availability", description = "Doctor daily presence management")
public class DoctorAvailabilityController {

    private final DoctorAvailabilityService availabilityService;

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN', 'DOCTOR')")
    public ResponseEntity<DoctorAvailabilityResponse> getAvailability(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(availabilityService.getAvailability(doctorId, date));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<DoctorAvailabilityResponse> createAvailability(
            @PathVariable Long doctorId,
            @Valid @RequestBody DoctorAvailabilityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(availabilityService.upsertAvailability(doctorId, request));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<DoctorAvailabilityResponse> updateAvailability(
            @PathVariable Long doctorId,
            @Valid @RequestBody DoctorAvailabilityRequest request) {
        return ResponseEntity.ok(availabilityService.upsertAvailability(doctorId, request));
    }
}
