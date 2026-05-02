package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.response.IpdBookingContextResponse;
import com.curamatrix.hsm.service.IpdReceptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ipd")
@RequiredArgsConstructor
@Tag(name = "13. IPD Reception", description = "IPD Admission Context for the Admission Wizard")
public class IpdReceptionController {

    private final IpdReceptionService ipdReceptionService;

    @GetMapping("/admission-context/{patientId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    @Operation(summary = "Get IPD admission context — doctors, available beds, insurance policies, and recent appointments for a patient")
    public ResponseEntity<IpdBookingContextResponse> getAdmissionContext(@PathVariable Long patientId) {
        return ResponseEntity.ok(ipdReceptionService.getAdmissionContext(patientId));
    }
}
