package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.response.DashboardStatsResponse;
import com.curamatrix.hsm.dto.response.DoctorQueueSummary;
import com.curamatrix.hsm.dto.response.QueueEntryResponse;
import com.curamatrix.hsm.entity.Appointment;
import com.curamatrix.hsm.entity.Doctor;
import com.curamatrix.hsm.enums.AppointmentStatus;
import com.curamatrix.hsm.repository.AppointmentRepository;
import com.curamatrix.hsm.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QueueService {

    private static final int AVG_CONSULTATION_MINUTES = 15;
    private static final int WAIT_ALERT_MINUTES = 30;

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;

    public List<QueueEntryResponse> getTodayQueue() {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();
        return appointmentRepository.findAllByDateAndTenant(today, tenantId)
                .stream().map(this::toQueueEntry).collect(Collectors.toList());
    }

    public List<QueueEntryResponse> getDoctorQueue(Long doctorId) {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();
        List<Appointment> queue = appointmentRepository
                .findTodayQueueByDoctorAndTenant(doctorId, today, tenantId);
        return IntStream.range(0, queue.size())
                .mapToObj(i -> toQueueEntry(queue.get(i), i + 1,
                        countCheckedInAhead(queue, i) * AVG_CONSULTATION_MINUTES))
                .collect(Collectors.toList());
    }

    public DashboardStatsResponse getDashboardStats() {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();

        Map<AppointmentStatus, Long> counts = new EnumMap<>(AppointmentStatus.class);
        appointmentRepository.countByStatusForDate(today, tenantId)
                .forEach(row -> counts.put((AppointmentStatus) row[0], (Long) row[1]));

        List<Appointment> checkedIn = appointmentRepository
                .findCheckedInByDateAndTenant(today, tenantId);
        LocalDateTime alertThreshold = LocalDateTime.now().minusMinutes(WAIT_ALERT_MINUTES);
        List<QueueEntryResponse> alerts = checkedIn.stream()
                .filter(a -> a.getCheckedInAt() != null && a.getCheckedInAt().isBefore(alertThreshold))
                .map(this::toQueueEntry).collect(Collectors.toList());

        List<DoctorQueueSummary> topDoctors = buildTopDoctorSummaries(today, tenantId);

        return DashboardStatsResponse.builder()
                .date(today)
                .totalBooked(counts.getOrDefault(AppointmentStatus.BOOKED, 0L))
                .totalCheckedIn(counts.getOrDefault(AppointmentStatus.CHECKED_IN, 0L))
                .totalInProgress(counts.getOrDefault(AppointmentStatus.IN_PROGRESS, 0L))
                .totalCompleted(counts.getOrDefault(AppointmentStatus.COMPLETED, 0L))
                .totalCancelled(counts.getOrDefault(AppointmentStatus.CANCELLED, 0L))
                .totalNoShow(counts.getOrDefault(AppointmentStatus.NO_SHOW, 0L))
                .waitingPatients(checkedIn.stream().map(this::toQueueEntry).collect(Collectors.toList()))
                .alertPatients(alerts)
                .topDoctors(topDoctors)
                .build();
    }

    private int countCheckedInAhead(List<Appointment> queue, int currentIndex) {
        return (int) queue.subList(0, currentIndex).stream()
                .filter(a -> a.getStatus() == AppointmentStatus.CHECKED_IN
                          || a.getStatus() == AppointmentStatus.IN_PROGRESS)
                .count();
    }

    private QueueEntryResponse toQueueEntry(Appointment a) {
        return toQueueEntry(a, null, null);
    }

    private QueueEntryResponse toQueueEntry(Appointment a, Integer position, Integer estWaitMinutes) {
        return QueueEntryResponse.builder()
                .appointmentId(a.getId())
                .patientId(a.getPatient().getId())
                .patientName(a.getPatient().getFirstName() + " " + a.getPatient().getLastName())
                .patientCode(a.getPatient().getPatientCode())
                .doctorId(a.getDoctor().getId())
                .doctorName(a.getDoctor().getUser().getFullName())
                .tokenNumber(a.getTokenNumber())
                .appointmentTime(a.getAppointmentTime())
                .status(a.getStatus())
                .type(a.getType())
                .checkedInAt(a.getCheckedInAt())
                .queuePosition(position)
                .estimatedWaitMinutes(estWaitMinutes)
                .build();
    }

    private List<DoctorQueueSummary> buildTopDoctorSummaries(LocalDate date, Long tenantId) {
        return appointmentRepository.findAllByDateAndTenant(date, tenantId).stream()
                .collect(Collectors.groupingBy(a -> a.getDoctor().getId()))
                .entrySet().stream()
                .map(e -> {
                    List<Appointment> appts = e.getValue();
                    Doctor doc = appts.get(0).getDoctor();
                    long active = appts.stream()
                            .filter(a -> a.getStatus() == AppointmentStatus.BOOKED
                                      || a.getStatus() == AppointmentStatus.CHECKED_IN
                                      || a.getStatus() == AppointmentStatus.IN_PROGRESS)
                            .count();
                    return DoctorQueueSummary.builder()
                            .doctorId(doc.getId())
                            .doctorName(doc.getUser().getFullName())
                            .activeQueueLength((int) active)
                            .totalToday(appts.size())
                            .build();
                })
                .sorted(Comparator.comparingInt(DoctorQueueSummary::getActiveQueueLength).reversed())
                .limit(5)
                .collect(Collectors.toList());
    }
}
