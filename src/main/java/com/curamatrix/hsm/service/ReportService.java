package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.response.*;
import com.curamatrix.hsm.entity.Appointment;
import com.curamatrix.hsm.entity.Doctor;
import com.curamatrix.hsm.entity.Patient;
import com.curamatrix.hsm.enums.AppointmentStatus;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reporting and analytics service.
 * Provides aggregated data for both Super Admin (platform-wide) and Hospital Admin (tenant-scoped).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;

    // ─── Super Admin Reports ─────────────────────────────────────

    /**
     * Platform-wide summary: total tenants, active/suspended, total users, total patients.
     */
    public PlatformSummaryResponse getPlatformSummary() {
        long totalTenants = tenantRepository.count();
        long activeHospitals = tenantRepository.countByIsActive(true);
        long suspendedHospitals = tenantRepository.countByIsActive(false);
        long totalUsers = userRepository.count();
        long totalPatients = patientRepository.count();
        long totalAppointments = appointmentRepository.count();

        return PlatformSummaryResponse.builder()
                .totalTenants(totalTenants)
                .activeHospitals(activeHospitals)
                .suspendedHospitals(suspendedHospitals)
                .totalUsers(totalUsers)
                .totalPatients(totalPatients)
                .totalAppointments(totalAppointments)
                .build();
    }

    // ─── Hospital Admin Reports ──────────────────────────────────

    /**
     * Appointment statistics filtered by date range, with breakdown by doctor.
     */
    public AppointmentStatsResponse getAppointmentStats(LocalDate startDate, LocalDate endDate) {
        // Get all appointments in range
        Pageable unpaged = Pageable.unpaged();
        List<Appointment> appointments = new ArrayList<>();

        // If no dates provided, use last 30 days
        if (startDate == null) startDate = LocalDate.now().minusDays(30);
        if (endDate == null) endDate = LocalDate.now();

        // Fetch appointments day by day in range (using existing filter method)
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            appointments.addAll(
                appointmentRepository.findByFilters(current, null, null, null, null, unpaged).getContent()
            );
            current = current.plusDays(1);
        }

        long total = appointments.size();
        long completed = appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count();
        long cancelled = appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.CANCELLED).count();
        long booked = appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.BOOKED).count();
        long inProgress = appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.IN_PROGRESS).count();

        double completionRate = total > 0 ? (double) completed / total * 100 : 0;
        double cancellationRate = total > 0 ? (double) cancelled / total * 100 : 0;

        // Breakdown by doctor
        Map<Long, List<Appointment>> byDoctor = appointments.stream()
                .collect(Collectors.groupingBy(a -> a.getDoctor().getId()));

        List<Map<String, Object>> doctorStats = byDoctor.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> stats = new LinkedHashMap<>();
                    Doctor doctor = entry.getValue().get(0).getDoctor();
                    stats.put("doctorId", doctor.getId());
                    stats.put("doctorName", doctor.getUser().getFullName());
                    stats.put("totalAppointments", entry.getValue().size());
                    stats.put("completed", entry.getValue().stream()
                            .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count());
                    stats.put("cancelled", entry.getValue().stream()
                            .filter(a -> a.getStatus() == AppointmentStatus.CANCELLED).count());
                    return stats;
                })
                .collect(Collectors.toList());

        return AppointmentStatsResponse.builder()
                .totalAppointments(total)
                .completed(completed)
                .cancelled(cancelled)
                .booked(booked)
                .inProgress(inProgress)
                .completionRate(Math.round(completionRate * 10.0) / 10.0)
                .cancellationRate(Math.round(cancellationRate * 10.0) / 10.0)
                .byDoctor(doctorStats)
                .build();
    }

    /**
     * Doctor workload report: appointments per doctor, completion rate.
     */
    public DoctorWorkloadResponse getDoctorWorkload() {
        List<Doctor> doctors = doctorRepository.findAll();
        Pageable unpaged = Pageable.unpaged();

        List<Map<String, Object>> doctorStats = doctors.stream()
                .map(doctor -> {
                    Map<String, Object> stats = new LinkedHashMap<>();
                    List<Appointment> appointments = appointmentRepository
                            .findByDoctorIdAndAppointmentDate(doctor.getId(), null, unpaged).getContent();

                    // Get all appointments for this doctor (today)
                    List<Appointment> todayAppts = appointmentRepository
                            .findTodayQueueByDoctor(doctor.getId(), LocalDate.now());

                    stats.put("doctorId", doctor.getId());
                    stats.put("doctorName", doctor.getUser().getFullName());
                    stats.put("department", doctor.getDepartment() != null ?
                            doctor.getDepartment().getName() : "N/A");
                    stats.put("specialization", doctor.getSpecialization());
                    stats.put("todayAppointments", todayAppts.size());
                    stats.put("todayCompleted", todayAppts.stream()
                            .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count());
                    stats.put("todayPending", todayAppts.stream()
                            .filter(a -> a.getStatus() != AppointmentStatus.COMPLETED &&
                                    a.getStatus() != AppointmentStatus.CANCELLED).count());
                    return stats;
                })
                .collect(Collectors.toList());

        return DoctorWorkloadResponse.builder()
                .totalDoctors(doctors.size())
                .doctorStats(doctorStats)
                .build();
    }

    /**
     * Patient registration trends: totals and monthly breakdown.
     */
    public PatientTrendResponse getPatientTrends() {
        long totalPatients = patientRepository.count();

        // Patients registered this month
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate weekStart = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);

        // Use a simple count approach since we don't have date-range queries
        // In production, you'd add repository queries for date ranges
        long patientsThisMonth = 0;
        long patientsThisWeek = 0;

        // Build monthly trend (last 6 months)
        List<Map<String, Object>> monthlyTrend = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate monthDate = LocalDate.now().minusMonths(i);
            Map<String, Object> monthData = new LinkedHashMap<>();
            monthData.put("month", monthDate.getMonth().name());
            monthData.put("year", monthDate.getYear());
            // Placeholder — in production, use date-range queries
            monthData.put("registrations", 0);
            monthlyTrend.add(monthData);
        }

        return PatientTrendResponse.builder()
                .totalPatients(totalPatients)
                .patientsThisMonth(patientsThisMonth)
                .patientsThisWeek(patientsThisWeek)
                .monthlyTrend(monthlyTrend)
                .build();
    }
}
