package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.response.DashboardStatsResponse;
import com.curamatrix.hsm.dto.response.QueueEntryResponse;
import com.curamatrix.hsm.service.QueueService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
@Tag(name = "5. Queue", description = "Real-time queue management for receptionist and doctor")
public class QueueController {

    private final QueueService queueService;

    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<List<QueueEntryResponse>> getTodayQueue() {
        return ResponseEntity.ok(queueService.getTodayQueue());
    }

    @GetMapping("/doctor/{doctorId}/today")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<List<QueueEntryResponse>> getDoctorQueue(@PathVariable Long doctorId) {
        return ResponseEntity.ok(queueService.getDoctorQueue(doctorId));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<DashboardStatsResponse> getDashboardStats() {
        return ResponseEntity.ok(queueService.getDashboardStats());
    }
}
