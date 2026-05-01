package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.PreAuthRequestDto;
import com.curamatrix.hsm.dto.response.PreAuthResponseDto;
import com.curamatrix.hsm.service.PreAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pre-auths")
@RequiredArgsConstructor
@Tag(name = "Pre-Authorisation", description = "Endpoints for managing TPA Pre-auth requests")
public class PreAuthController {

    private final PreAuthService preAuthService;

    @Operation(summary = "Create PreAuth request")
    @PostMapping
    public ResponseEntity<PreAuthResponseDto> createPreAuth(@RequestBody PreAuthRequestDto request) {
        return ResponseEntity.ok(preAuthService.createPreAuth(request));
    }

    @Operation(summary = "Update PreAuth request")
    @PutMapping("/{id}")
    public ResponseEntity<PreAuthResponseDto> updatePreAuth(@PathVariable Long id, @RequestBody PreAuthRequestDto request) {
        return ResponseEntity.ok(preAuthService.updatePreAuth(id, request));
    }

    @Operation(summary = "Get all PreAuth requests (Paginated)")
    @GetMapping
    public ResponseEntity<Page<PreAuthResponseDto>> getAllPreAuths(Pageable pageable) {
        return ResponseEntity.ok(preAuthService.getAllPreAuths(pageable));
    }

    @Operation(summary = "Get PreAuth requests by patient (Paginated)")
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<Page<PreAuthResponseDto>> getPreAuthsByPatient(@PathVariable Long patientId, Pageable pageable) {
        return ResponseEntity.ok(preAuthService.getPreAuthsByPatient(patientId, pageable));
    }
}
