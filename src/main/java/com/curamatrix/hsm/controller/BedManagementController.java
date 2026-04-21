package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.BedRequest;
import com.curamatrix.hsm.dto.request.RoomRequest;
import com.curamatrix.hsm.dto.request.WardRequest;
import com.curamatrix.hsm.dto.response.BedResponse;
import com.curamatrix.hsm.dto.response.RoomResponse;
import com.curamatrix.hsm.dto.response.WardResponse;
import com.curamatrix.hsm.enums.BedStatus;
import com.curamatrix.hsm.service.BedManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/bed-management")
@RequiredArgsConstructor
@Tag(name = "11. IPD Bed Management", description = "Manage Wards, Rooms, and Beds")
public class BedManagementController {

    private final BedManagementService bedManagementService;

    // --- Wards ---

    @PostMapping("/wards")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Create a new Ward")
    public ResponseEntity<WardResponse> createWard(@Valid @RequestBody WardRequest request) {
        log.info("Creating ward: {}", request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(bedManagementService.createWard(request));
    }

    @GetMapping("/wards")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    @Operation(summary = "Get all wards. Supports nested matrix view.")
    public ResponseEntity<List<WardResponse>> getAllWards(
            @RequestParam(defaultValue = "false") boolean includeHierarchy) {
        return ResponseEntity.ok(bedManagementService.getAllWards(includeHierarchy));
    }

    // --- Rooms ---

    @PostMapping("/rooms")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Create a new Room")
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bedManagementService.createRoom(request));
    }

    @GetMapping("/wards/{wardId}/rooms")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<List<RoomResponse>> getRoomsByWard(@PathVariable Long wardId) {
        return ResponseEntity.ok(bedManagementService.getRoomsByWard(wardId));
    }

    // --- Beds ---

    @PostMapping("/beds")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Create a new Bed")
    public ResponseEntity<BedResponse> createBed(@Valid @RequestBody BedRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bedManagementService.createBed(request));
    }

    @PatchMapping("/beds/{id}/status")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Manually update bed status (e.g. mark for cleaning)")
    public ResponseEntity<BedResponse> updateBedStatus(@PathVariable Long id, @RequestParam BedStatus status) {
        return ResponseEntity.ok(bedManagementService.updateBedStatus(id, status));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    @Operation(summary = "Get bed occupancy stats with ward and room-type breakdowns")
    public ResponseEntity<Map<String, Object>> getBedStats() {
        return ResponseEntity.ok(bedManagementService.getBedStats());
    }
}
