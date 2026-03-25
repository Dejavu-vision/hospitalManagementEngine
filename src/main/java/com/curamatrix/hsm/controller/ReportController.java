package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.response.*;
import com.curamatrix.hsm.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Reporting and analytics endpoints for Super Admin and Hospital Admin.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // ─── Super Admin Reports ─────────────────────────────────────

    @GetMapping("/api/super-admin/reports/summary")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Tag(name = "Super Admin - Reports", description = "Platform-wide analytics for Super Admin")
    @Operation(summary = "Platform Summary",
            description = "Returns platform-wide metrics: total tenants, active/suspended hospitals, " +
                    "total users, patients, and appointments.")
    public ResponseEntity<PlatformSummaryResponse> getPlatformSummary() {
        log.info("Generating platform summary report");
        PlatformSummaryResponse summary = reportService.getPlatformSummary();
        return ResponseEntity.ok(summary);
    }

    // ─── Hospital Admin Reports ──────────────────────────────────

    @GetMapping("/api/admin/reports/appointments")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Tag(name = "Hospital Admin - Reports", description = "Hospital-scoped analytics for Admin")
    @Operation(summary = "Appointment Statistics",
            description = "Returns appointment volume, completion rate, cancellation rate, and " +
                    "per-doctor breakdown for a given date range (defaults to last 30 days).")
    public ResponseEntity<AppointmentStatsResponse> getAppointmentStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Generating appointment stats: {} to {}", startDate, endDate);
        AppointmentStatsResponse stats = reportService.getAppointmentStats(startDate, endDate);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/api/admin/reports/doctors")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Tag(name = "Hospital Admin - Reports")
    @Operation(summary = "Doctor Workload Report",
            description = "Returns today's appointment count, completed count, and pending count per doctor.")
    public ResponseEntity<DoctorWorkloadResponse> getDoctorWorkload() {
        log.info("Generating doctor workload report");
        DoctorWorkloadResponse workload = reportService.getDoctorWorkload();
        return ResponseEntity.ok(workload);
    }

    @GetMapping("/api/admin/reports/patients")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Tag(name = "Hospital Admin - Reports")
    @Operation(summary = "Patient Registration Trends",
            description = "Returns total patients, this month/week counts, and monthly registration trend.")
    public ResponseEntity<PatientTrendResponse> getPatientTrends() {
        log.info("Generating patient trend report");
        PatientTrendResponse trends = reportService.getPatientTrends();
        return ResponseEntity.ok(trends);
    }
}
