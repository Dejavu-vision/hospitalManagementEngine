package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.response.*;
import com.curamatrix.hsm.entity.Appointment;
import com.curamatrix.hsm.entity.Doctor;
import com.curamatrix.hsm.entity.DoctorAvailability;
import com.curamatrix.hsm.enums.AppointmentStatus;
import com.curamatrix.hsm.enums.DoctorStatus;
import com.curamatrix.hsm.repository.AppointmentRepository;
import com.curamatrix.hsm.repository.DoctorAvailabilityRepository;
import com.curamatrix.hsm.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorAvailabilityRepository doctorAvailabilityRepository;

    // ── Legacy endpoints (kept for backward compatibility) ────────────────────

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

        // Build a minimal activeQueues list for counter label lookup
        List<QueueDashboardResponse.ActiveQueueSummary> activeQueues = List.of(
                QueueDashboardResponse.ActiveQueueSummary.builder()
                        .doctorId(doctorId)
                        .counterLabel("Counter 1")
                        .build()
        );

        return IntStream.range(0, queue.size())
                .mapToObj(i -> {
                    Appointment a = queue.get(i);
                    List<Appointment> activeAhead = queue.subList(0, i).stream()
                            .filter(x -> x.getStatus() == AppointmentStatus.BOOKED
                                      || x.getStatus() == AppointmentStatus.CHECKED_IN)
                            .collect(Collectors.toList());
                    int estWait = activeAhead.size() * AVG_CONSULTATION_MINUTES;
                    return toRichQueueEntry(a, i + 1, estWait, activeQueues);
                })
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

    // ── New rich dashboard endpoint ───────────────────────────────────────────

    public QueueDashboardResponse getQueueDashboard(Long selectedDoctorId) {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // All today's appointments (all statuses — for stats and recent activity)
        // Deduplicate by appointment ID in case JOIN FETCH produces duplicates
        List<Appointment> allToday = appointmentRepository.findAllByDateAndTenant(today, tenantId)
                .stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(a -> a.getId(), a -> a, (a, b) -> a, java.util.LinkedHashMap::new),
                        map -> new ArrayList<>(map.values())
                ));

        // Active only (BOOKED, CHECKED_IN, IN_PROGRESS, ON_HOLD, RECALLED) — for queue logic
        List<Appointment> activeToday = allToday.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.BOOKED
                          || a.getStatus() == AppointmentStatus.CHECKED_IN
                          || a.getStatus() == AppointmentStatus.IN_PROGRESS
                          || a.getStatus() == AppointmentStatus.ON_HOLD
                          || a.getStatus() == AppointmentStatus.RECALLED)
                .collect(Collectors.toList());

        // Status counts
        Map<AppointmentStatus, Long> counts = new EnumMap<>(AppointmentStatus.class);
        appointmentRepository.countByStatusForDate(today, tenantId)
                .forEach(row -> counts.put((AppointmentStatus) row[0], (Long) row[1]));

        long totalTokens = allToday.size();
        long waiting = counts.getOrDefault(AppointmentStatus.BOOKED, 0L)
                     + counts.getOrDefault(AppointmentStatus.CHECKED_IN, 0L)
                     + counts.getOrDefault(AppointmentStatus.RECALLED, 0L);
        long served = counts.getOrDefault(AppointmentStatus.COMPLETED, 0L);
        long noShows = counts.getOrDefault(AppointmentStatus.NO_SHOW, 0L);

        // Doctor availability map
        List<DoctorAvailability> availabilities = doctorAvailabilityRepository
                .findByAvailabilityDateAndTenantId(today, tenantId);
        Map<Long, DoctorAvailability> availMap = availabilities.stream()
                .collect(Collectors.toMap(da -> da.getDoctor().getId(), da -> da, (a, b) -> a));

        // Build one entry per doctor — use LinkedHashMap for stable insertion order
        // Sort by doctorId so counter numbers are consistent across calls
        Map<Long, Doctor> doctorMap = new java.util.LinkedHashMap<>();
        allToday.stream()
                .sorted(Comparator.comparingLong(a -> a.getDoctor().getId()))
                .forEach(a -> doctorMap.putIfAbsent(a.getDoctor().getId(), a.getDoctor()));

        // Build active queue summaries (left sidebar) — one entry per unique doctor
        List<QueueDashboardResponse.ActiveQueueSummary> activeQueues = new ArrayList<>();
        int counterIndex = 1;
        for (Map.Entry<Long, Doctor> entry : doctorMap.entrySet()) {
            Long doctorId = entry.getKey();
            Doctor doc = entry.getValue();

            List<Appointment> allForDoc = allToday.stream()
                    .filter(a -> a.getDoctor().getId().equals(doctorId))
                    .collect(Collectors.toList());
            List<Appointment> activeForDoc = activeToday.stream()
                    .filter(a -> a.getDoctor().getId().equals(doctorId))
                    .collect(Collectors.toList());

            DoctorAvailability avail = availMap.get(doctorId);
            DoctorStatus status = avail != null ? avail.getStatus() : DoctorStatus.OFF_DUTY;

            long waitingForDoc = activeForDoc.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.BOOKED
                              || a.getStatus() == AppointmentStatus.CHECKED_IN
                              || a.getStatus() == AppointmentStatus.RECALLED)
                    .count();

            Appointment inProgress = activeForDoc.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.IN_PROGRESS
                              || a.getStatus() == AppointmentStatus.RECALLED)
                    .findFirst().orElse(null);

            String currentToken = inProgress != null
                    ? formatToken(inProgress.getTokenNumber(), inProgress.getType())
                    : "—";

            int avgWait = computeAvgWaitMinutes(allForDoc);
            String deptName = doc.getDepartment() != null ? doc.getDepartment().getName() : "General";

            // Determine counter label — Lab gets special label
            boolean isLab = deptName.toLowerCase().contains("lab");
            String counterLabel = isLab ? "Lab Counter" : "Counter " + counterIndex;

            activeQueues.add(QueueDashboardResponse.ActiveQueueSummary.builder()
                    .doctorId(doctorId)
                    .doctorName(doc.getUser().getFullName())
                    .qualification(doc.getQualification())
                    .departmentId(doc.getDepartment() != null ? doc.getDepartment().getId() : null)
                    .departmentName(deptName)
                    .counterLabel(counterLabel)
                    .doctorStatus(status)
                    .statusLabel(toStatusLabel(status))
                    .waitingCount((int) waitingForDoc)
                    .currentTokenDisplay(currentToken)
                    .avgWaitMinutes(avgWait)
                    .build());
            counterIndex++;
        }

        // Currently serving — pick from selected doctor or first IN_PROGRESS/RECALLED
        Appointment serving = null;
        if (selectedDoctorId != null) {
            serving = activeToday.stream()
                    .filter(a -> a.getDoctor().getId().equals(selectedDoctorId)
                              && (a.getStatus() == AppointmentStatus.IN_PROGRESS
                               || a.getStatus() == AppointmentStatus.RECALLED))
                    .findFirst().orElse(null);
        }
        if (serving == null) {
            serving = activeToday.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.IN_PROGRESS
                              || a.getStatus() == AppointmentStatus.RECALLED)
                    .findFirst().orElse(null);
        }

        QueueEntryResponse currentlyServing = serving != null
                ? toRichQueueEntry(serving, 0, 0, activeQueues) : null;

        // Determine which doctor's queue to show in the centre panel
        Long queueDoctorId = selectedDoctorId != null ? selectedDoctorId
                : (serving != null ? serving.getDoctor().getId()
                : (!activeQueues.isEmpty() ? activeQueues.get(0).getDoctorId() : null));

        // Waiting queue — ALL of today's appointments for the selected doctor
        // (includes COMPLETED/NO_SHOW so the "Completed" tab works)
        // docQueue is fetched once and reused for both waitingQueue and heldPatients
        List<QueueEntryResponse> waitingQueue = new ArrayList<>();
        List<QueueEntryResponse> heldPatients = new ArrayList<>();
        String waitingQueueLabel = "Waiting Queue";
        if (queueDoctorId != null) {
            List<Appointment> docQueue = appointmentRepository
                    .findTodayQueueByDoctorAndTenant(queueDoctorId, today, tenantId);
            Doctor queueDoc = docQueue.isEmpty() ? null : docQueue.get(0).getDoctor();
            if (queueDoc != null && queueDoc.getDepartment() != null) {
                waitingQueueLabel = "Waiting Queue — " + queueDoc.getDepartment().getName();
            }
            // Only count BOOKED/CHECKED_IN as "ahead" for wait time
            List<Appointment> activeDocQueue = docQueue.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.BOOKED
                              || a.getStatus() == AppointmentStatus.CHECKED_IN
                              || a.getStatus() == AppointmentStatus.IN_PROGRESS
                              || a.getStatus() == AppointmentStatus.RECALLED)
                    .collect(Collectors.toList());

            for (int i = 0; i < docQueue.size(); i++) {
                Appointment a = docQueue.get(i);
                // Skip CANCELLED appointments — they should not appear in the queue table
                if (a.getStatus() == AppointmentStatus.CANCELLED) continue;
                // Estimate wait: count only BOOKED/CHECKED_IN patients ahead (not IN_PROGRESS)
                int aheadIdx = activeDocQueue.indexOf(a);
                int ahead = aheadIdx > 0
                        ? (int) activeDocQueue.subList(0, aheadIdx).stream()
                            .filter(x -> x.getStatus() == AppointmentStatus.BOOKED
                                      || x.getStatus() == AppointmentStatus.CHECKED_IN)
                            .count()
                        : 0;
                waitingQueue.add(toRichQueueEntry(a, i + 1, ahead * AVG_CONSULTATION_MINUTES, activeQueues));
            }

            // Build held patients list from the same docQueue (no extra DB fetch)
            heldPatients = buildHeldPatients(docQueue, activeQueues, now);
        }
        long onHoldCount = heldPatients.size();

        // Counter statuses (right panel) — derived directly from activeQueues (already deduplicated)
        List<QueueDashboardResponse.CounterStatus> counterStatuses = activeQueues.stream()
                .map(aq -> QueueDashboardResponse.CounterStatus.builder()
                        .counterLabel(aq.getCounterLabel().toUpperCase())
                        .tokenDisplay(aq.getCurrentTokenDisplay())
                        .doctorName(aq.getDoctorName())
                        .specialty(aq.getDepartmentName())
                        .doctorStatus(aq.getDoctorStatus())
                        .statusLabel(aq.getStatusLabel())
                        .isLabCounter(aq.getDepartmentName() != null
                                && aq.getDepartmentName().toLowerCase().contains("lab"))
                        .build())
                .collect(Collectors.toList());

        int avgWaitMinutes = computeAvgWaitMinutesAll(allToday);
        int longestWait = computeLongestWaitMinutes(activeToday, now);
        String peakHour = computePeakHour(allToday);
        List<QueueDashboardResponse.RecentActivity> recentActivity = buildRecentActivity(allToday);

        return QueueDashboardResponse.builder()
                .date(today)
                .totalTokensToday(totalTokens)
                .currentlyWaiting(waiting)
                .servedToday(served)
                .noShows(noShows)
                .avgConsultMinutes(AVG_CONSULTATION_MINUTES)
                .targetConsultMinutes(10)
                .tokensDeltaFromYesterday(0L)
                .activeQueues(activeQueues)
                .currentlyServing(currentlyServing)
                .waitingQueue(waitingQueue)
                .waitingQueueLabel(waitingQueueLabel)
                .waitingQueueTotal(waitingQueue.size())
                .heldPatients(heldPatients)
                .onHoldCount(onHoldCount)
                .counterStatuses(counterStatuses)
                .tokensIssued(totalTokens)
                .served(served)
                .waiting(waiting)
                .noShowCount(noShows)
                .avgWaitMinutes(avgWaitMinutes)
                .longestWaitMinutes(longestWait)
                .peakHour(peakHour)
                .recentActivity(recentActivity)
                .smsEnabled(true)
                .smsAlertPosition(3)
                .smsSentToday(served)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
                .tokenDisplay(formatToken(a.getTokenNumber(), a.getType()))
                .appointmentTime(a.getAppointmentTime())
                .status(a.getStatus())
                .type(a.getType())
                .checkedInAt(a.getCheckedInAt())
                .queuePosition(position)
                .estimatedWaitMinutes(estWaitMinutes)
                .build();
    }

    private QueueEntryResponse toRichQueueEntry(Appointment a, int position, int estWaitMinutes,
                                                  List<QueueDashboardResponse.ActiveQueueSummary> activeQueues) {
        String patientName = a.getPatient().getFirstName() + " " + a.getPatient().getLastName();
        String deptName = a.getDoctor().getDepartment() != null
                ? a.getDoctor().getDepartment().getName() : "General";
        Long deptId = a.getDoctor().getDepartment() != null
                ? a.getDoctor().getDepartment().getId() : null;

        // Find counter label for this doctor
        String counterLabel = activeQueues.stream()
                .filter(aq -> aq.getDoctorId().equals(a.getDoctor().getId()))
                .map(QueueDashboardResponse.ActiveQueueSummary::getCounterLabel)
                .findFirst().orElse("Counter 1");

        // Compute actual wait minutes
        int waitMinutes = 0;
        if (a.getCheckedInAt() != null) {
            waitMinutes = (int) ChronoUnit.MINUTES.between(a.getCheckedInAt(), LocalDateTime.now());
            if (waitMinutes < 0) waitMinutes = 0;
        }

        // Visit type
        String visitType = a.getType() == com.curamatrix.hsm.enums.AppointmentType.WALK_IN
                ? "OPD - New Visit" : "OPD - Follow-up";

        // Registered at
        String registeredAt = a.getCreatedAt() != null
                ? a.getCreatedAt().format(TIME_FMT) : "";

        // UHID
        String uhid = "UHID-" + LocalDate.now().getYear() + "-"
                + String.format("%05d", a.getPatient().getId());

        // Patient age/gender placeholder (Patient entity may not have DOB/gender — use code)
        String patientAge = a.getPatient().getPatientCode() != null
                ? a.getPatient().getPatientCode() : "";

        return QueueEntryResponse.builder()
                .appointmentId(a.getId())
                .patientId(a.getPatient().getId())
                .patientName(patientName)
                .patientCode(a.getPatient().getPatientCode())
                .patientAge(patientAge)
                .doctorId(a.getDoctor().getId())
                .doctorName(a.getDoctor().getUser().getFullName())
                .doctorQualification(a.getDoctor().getQualification())
                .departmentId(deptId)
                .departmentName(deptName)
                .counterLabel(counterLabel)
                .tokenNumber(a.getTokenNumber())
                .tokenDisplay(formatToken(a.getTokenNumber(), a.getType()))
                .appointmentTime(a.getAppointmentTime())
                .status(a.getStatus())
                .type(a.getType())
                .visitType(visitType)
                .priorityCategory("REGULAR")
                .priorityLabel("Normal")
                .checkedInAt(a.getCheckedInAt())
                .queuePosition(position)
                .estimatedWaitMinutes(estWaitMinutes)
                .waitingMinutes(waitMinutes)
                .registeredAt(registeredAt)
                .waitDuration(waitMinutes > 0 ? waitMinutes + " min" : "—")
                .uhid(uhid)
                .recallCount(a.getRecallCount())
                .heldAt(a.getHeldAt())
                .holdMinutes(a.getHeldAt() != null
                        ? Math.max(0, (int) ChronoUnit.MINUTES.between(a.getHeldAt(), LocalDateTime.now()))
                        : null)
                .build();
    }

    private String formatToken(Integer tokenNumber, com.curamatrix.hsm.enums.AppointmentType type) {
        if (tokenNumber == null) return "—";
        if (type == com.curamatrix.hsm.enums.AppointmentType.WALK_IN) {
            return "T-" + String.format("%03d", tokenNumber);
        }
        return "S-" + String.format("%03d", tokenNumber);
    }

    private String toStatusLabel(DoctorStatus status) {
        if (status == null) return "Idle";
        return switch (status) {
            case ON_DUTY -> "Active";
            case IN_CONSULTATION -> "Busy";
            case ON_BREAK -> "Break";
            case IN_SURGERY -> "Surgery";
            case OFF_DUTY -> "Idle";
        };
    }

    private int computeAvgWaitMinutes(List<Appointment> appts) {
        List<Long> waits = appts.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED
                          && a.getCheckedInAt() != null && a.getConsultationStart() != null)
                .map(a -> ChronoUnit.MINUTES.between(a.getCheckedInAt(), a.getConsultationStart()))
                .filter(m -> m >= 0 && m < 300)
                .collect(Collectors.toList());
        if (waits.isEmpty()) return AVG_CONSULTATION_MINUTES;
        return (int) waits.stream().mapToLong(Long::longValue).average().orElse(AVG_CONSULTATION_MINUTES);
    }

    private int computeAvgWaitMinutesAll(List<Appointment> appts) {
        return computeAvgWaitMinutes(appts);
    }

    private int computeLongestWaitMinutes(List<Appointment> appts, LocalDateTime now) {
        return appts.stream()
                .filter(a -> (a.getStatus() == AppointmentStatus.CHECKED_IN
                           || a.getStatus() == AppointmentStatus.BOOKED)
                          && a.getCheckedInAt() != null)
                .mapToInt(a -> (int) ChronoUnit.MINUTES.between(a.getCheckedInAt(), now))
                .filter(m -> m >= 0)
                .max().orElse(0);
    }

    private String computePeakHour(List<Appointment> appts) {
        Map<Integer, Long> hourCounts = appts.stream()
                .filter(a -> a.getCheckedInAt() != null)
                .collect(Collectors.groupingBy(
                        a -> a.getCheckedInAt().getHour(), Collectors.counting()));
        if (hourCounts.isEmpty()) return "10–11 AM";
        int peakH = hourCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(10);
        String ampm = peakH < 12 ? "AM" : "PM";
        int h12 = peakH % 12 == 0 ? 12 : peakH % 12;
        int next = (peakH + 1) % 12 == 0 ? 12 : (peakH + 1) % 12;
        String nextAmpm = (peakH + 1) < 12 ? "AM" : "PM";
        return h12 + "–" + next + " " + (ampm.equals(nextAmpm) ? ampm : nextAmpm);
    }

    private List<QueueDashboardResponse.RecentActivity> buildRecentActivity(List<Appointment> appts) {
        List<QueueDashboardResponse.RecentActivity> activities = new ArrayList<>();

        // IN_PROGRESS → "Called"
        appts.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.IN_PROGRESS
                          && a.getConsultationStart() != null)
                .sorted(Comparator.comparing(Appointment::getConsultationStart).reversed())
                .limit(2)
                .forEach(a -> activities.add(QueueDashboardResponse.RecentActivity.builder()
                        .type("CALLED")
                        .tokenDisplay(formatToken(a.getTokenNumber(), a.getType()))
                        .patientName(a.getPatient().getFirstName() + " " + a.getPatient().getLastName())
                        .detail(getDeptName(a) + " · " + "Counter 1")
                        .timeAgo(a.getConsultationStart().format(TIME_FMT))
                        .build()));

        // COMPLETED → "Marked Done"
        appts.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED
                          && a.getConsultationEnd() != null)
                .sorted(Comparator.comparing(Appointment::getConsultationEnd).reversed())
                .limit(2)
                .forEach(a -> activities.add(QueueDashboardResponse.RecentActivity.builder()
                        .type("DONE")
                        .tokenDisplay(formatToken(a.getTokenNumber(), a.getType()))
                        .patientName(a.getPatient().getFirstName() + " " + a.getPatient().getLastName())
                        .detail("Dr. " + a.getDoctor().getUser().getFullName())
                        .timeAgo(a.getConsultationEnd().format(TIME_FMT))
                        .build()));

        // NO_SHOW
        appts.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.NO_SHOW
                          && a.getNoShowMarkedAt() != null)
                .sorted(Comparator.comparing(Appointment::getNoShowMarkedAt).reversed())
                .limit(1)
                .forEach(a -> activities.add(QueueDashboardResponse.RecentActivity.builder()
                        .type("NO_SHOW")
                        .tokenDisplay(formatToken(a.getTokenNumber(), a.getType()))
                        .patientName(a.getPatient().getFirstName() + " " + a.getPatient().getLastName())
                        .detail(getDeptName(a))
                        .timeAgo(a.getNoShowMarkedAt().format(TIME_FMT))
                        .build()));

        // ON_HOLD → "Held"
        appts.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.ON_HOLD && a.getHeldAt() != null)
                .sorted(Comparator.comparing(Appointment::getHeldAt).reversed())
                .limit(1)
                .forEach(a -> activities.add(QueueDashboardResponse.RecentActivity.builder()
                        .type("HELD")
                        .tokenDisplay(formatToken(a.getTokenNumber(), a.getType()))
                        .patientName(a.getPatient().getFirstName() + " " + a.getPatient().getLastName())
                        .detail(getDeptName(a))
                        .timeAgo(a.getHeldAt().format(TIME_FMT))
                        .build()));

        // Sort by time descending and limit to 5
        return activities.stream().limit(5).collect(Collectors.toList());
    }

    private List<QueueEntryResponse> buildHeldPatients(List<Appointment> docQueue,
            List<QueueDashboardResponse.ActiveQueueSummary> activeQueues, LocalDateTime now) {
        return docQueue.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.ON_HOLD)
                .sorted(Comparator.comparing(Appointment::getHeldAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(a -> {
                    QueueEntryResponse entry = toRichQueueEntry(a, 0, 0, activeQueues);
                    int holdMins = 0;
                    if (a.getHeldAt() != null) {
                        holdMins = (int) ChronoUnit.MINUTES.between(a.getHeldAt(), now);
                        if (holdMins < 0) holdMins = 0;
                    }
                    entry.setHeldAt(a.getHeldAt());
                    entry.setHoldMinutes(holdMins);
                    return entry;
                })
                .collect(Collectors.toList());
    }

    private String getDeptName(Appointment a) {
        return a.getDoctor().getDepartment() != null
                ? a.getDoctor().getDepartment().getName() : "General";
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