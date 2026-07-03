package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.AppointmentRequest;
import com.curamatrix.hsm.dto.request.WalkInRequest;
import com.curamatrix.hsm.dto.response.AppointmentResponse;
import com.curamatrix.hsm.dto.response.SlotResponse;
import com.curamatrix.hsm.dto.response.StatusLogResponse;
import com.curamatrix.hsm.entity.Appointment;
import com.curamatrix.hsm.entity.AppointmentStatusLog;
import com.curamatrix.hsm.entity.Billing;
import com.curamatrix.hsm.entity.Doctor;
import com.curamatrix.hsm.entity.Patient;
import com.curamatrix.hsm.entity.User;
import com.curamatrix.hsm.entity.WalkInTokenSequence;
import com.curamatrix.hsm.enums.AppointmentStatus;
import com.curamatrix.hsm.enums.AppointmentType;
import com.curamatrix.hsm.exception.DuplicateResourceException;
import com.curamatrix.hsm.exception.InvalidStateTransitionException;
import com.curamatrix.hsm.exception.RegistrationPaymentPendingException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.AppointmentRepository;
import com.curamatrix.hsm.repository.AppointmentStatusLogRepository;
import com.curamatrix.hsm.repository.DoctorRepository;
import com.curamatrix.hsm.repository.PatientRepository;
import com.curamatrix.hsm.repository.UserRepository;
import com.curamatrix.hsm.repository.WalkInTokenSequenceRepository;
import com.curamatrix.hsm.service.QueueEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final AppointmentStatusLogRepository statusLogRepository;
    private final WalkInTokenSequenceRepository tokenSequenceRepository;
    private final BillingService billingService;
    private final BlockedTokenService blockedTokenService;
    private final QueueEventService queueEventService;

    @Transactional
    public AppointmentResponse bookAppointment(AppointmentRequest request) {
        // Validate slot availability
        appointmentRepository.findByDoctorAndDateAndTime(
                request.getDoctorId(), request.getAppointmentDate(), request.getAppointmentTime())
                .ifPresent(a -> {
                    throw new DuplicateResourceException(
                            "Time slot already booked for this doctor on " +
                            request.getAppointmentDate() + " at " + request.getAppointmentTime());
                });

        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", request.getPatientId()));
        Doctor doctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", request.getDoctorId()));
        User bookedBy = getCurrentUser();

        Appointment appointment = Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .bookedBy(bookedBy)
                .appointmentDate(request.getAppointmentDate())
                .appointmentTime(request.getAppointmentTime())
                .type(AppointmentType.SCHEDULED)
                .status(AppointmentStatus.BOOKED)
                .notes(request.getNotes())
                .build();

        appointment = appointmentRepository.save(appointment);
        log.info("Appointment booked: {}", appointment.getId());

        // Create billing
        Billing billing = null;
        if (!request.isFollowUp()) {
            billing = billingService.createAppointmentBilling(appointment, request.isPayNow());
        }

        AppointmentResponse response = mapToResponse(appointment);
        if (billing != null) response.setBillingId(billing.getId());
        return response;
    }

    @Transactional
    public AppointmentResponse createWalkIn(WalkInRequest request) {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();

        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", request.getPatientId()));

        // ── Auto-assign doctor when doctorId is null ──────────────────────────
        Doctor doctor;
        if (request.getDoctorId() != null) {
            doctor = doctorRepository.findById(request.getDoctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", request.getDoctorId()));
        } else {
            if (request.getDepartmentId() == null) {
                throw new IllegalArgumentException("Either doctorId or departmentId must be provided");
            }
            List<Doctor> candidates = doctorRepository.findPresentDoctorsByDepartmentAndTenant(
                    request.getDepartmentId(), tenantId);
            if (candidates.isEmpty()) {
                throw new ResourceNotFoundException("Doctor", "departmentId", request.getDepartmentId());
            }
            // Pick least-busy doctor (fewest active appointments today)
            doctor = candidates.stream()
                    .min(Comparator.comparingLong(d ->
                            appointmentRepository.countActiveByDoctorAndDate(d.getId(), today, tenantId)))
                    .orElse(candidates.get(0));
        }

        User bookedBy = getCurrentUser();

        // ── Encode counter into notes ─────────────────────────────────────────
        String encodedNotes = encodeCounter(request.getCounter(), request.getNotes());

        // ── Token assignment ──────────────────────────────────────────────────
        int nextToken;

        if (request.getBlockedTokenNumber() != null) {
            // Receptionist explicitly chose a blocked (reserved) token
            boolean isAvailable = blockedTokenService.isTokenBlocked(
                    request.getBlockedTokenNumber(), tenantId, doctor.getId());
            if (!isAvailable) {
                throw new IllegalArgumentException(
                        "Token T-" + String.format("%03d", request.getBlockedTokenNumber()) +
                        " is not available as a blocked token. It may have already been assigned or released.");
            }
            nextToken = request.getBlockedTokenNumber();
            // The blocked token record will be marked ASSIGNED after appointment is saved (below)
        } else {
            // Auto-increment — skip any currently blocked token numbers for this doctor
            WalkInTokenSequence seq = tokenSequenceRepository
                    .findForUpdate(today, tenantId, doctor.getId())
                    .orElseGet(() -> {
                        WalkInTokenSequence newSeq = WalkInTokenSequence.builder()
                                .appointmentDate(today).lastToken(0).build();
                        newSeq.setCounter(0);
                        newSeq.setDoctorId(doctor.getId());
                        return newSeq;
                    });

            // Increment, skipping blocked numbers for this doctor (max 100 attempts to avoid infinite loop)
            int candidate = seq.getLastToken() + 1;
            int attempts = 0;
            while (attempts < 100 && blockedTokenService.isTokenBlocked(candidate, tenantId, doctor.getId())) {
                candidate++;
                attempts++;
            }

            seq.setLastToken(candidate);
            seq.setCounter(candidate);
            tokenSequenceRepository.save(seq);
            nextToken = candidate;
        }

        Appointment appointment = Appointment.builder()
                .patient(patient).doctor(doctor).bookedBy(bookedBy)
                .appointmentDate(today).type(AppointmentType.WALK_IN)
                .tokenNumber(nextToken).status(AppointmentStatus.CHECKED_IN)
                .checkedInAt(LocalDateTime.now())
                .notes(encodedNotes).build();
        appointment = appointmentRepository.save(appointment);

        // If a blocked token was used, mark it as ASSIGNED
        if (request.getBlockedTokenNumber() != null) {
            blockedTokenService.assignBlockedToken(request.getBlockedTokenNumber(), appointment.getId(), tenantId, doctor.getId());
        }

        // 2. Create billing
        Billing billing = null;
        if (!request.isFollowUp()) {
            billing = billingService.createAppointmentBilling(appointment, request.isPayNow());
        }

        recordStatusLog(appointment, null, AppointmentStatus.CHECKED_IN, bookedBy);

        AppointmentResponse response = mapToResponse(appointment);
        if (billing != null) response.setBillingId(billing.getId());
        return response;
    }

    public AppointmentResponse getAppointmentById(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", id));
        return mapToResponse(appointment);
    }

    public Page<AppointmentResponse> getAppointments(LocalDate date, Long doctorId, Long patientId,
                                                     AppointmentStatus status, AppointmentType type, Pageable pageable) {
        return appointmentRepository.findByFilters(date, doctorId, patientId, status, type, pageable)
                .map(this::mapToResponse);
    }

    public SlotResponse getAvailableSlots(Long doctorId, LocalDate date) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", doctorId));

        List<String> allSlots = generateTimeSlots();
        List<Appointment> bookedAppointments = appointmentRepository.findByDoctorIdAndAppointmentDate(
                doctorId, date, Pageable.unpaged()).getContent();

        List<String> bookedSlots = bookedAppointments.stream()
                .filter(a -> a.getAppointmentTime() != null)
                .map(a -> a.getAppointmentTime().toString())
                .collect(Collectors.toList());

        List<String> availableSlots = allSlots.stream()
                .filter(slot -> !bookedSlots.contains(slot))
                .collect(Collectors.toList());

        return SlotResponse.builder()
                .doctorId(doctorId)
                .doctorName(doctor.getUser().getFullName())
                .date(date)
                .slotDurationMinutes(30)
                .availableSlots(availableSlots)
                .bookedSlots(bookedSlots)
                .build();
    }

    public List<AppointmentResponse> getTodayQueue(Long doctorId) {
        LocalDate today = LocalDate.now();
        return appointmentRepository.findTodayQueueByDoctor(doctorId, today).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Updates the appointment status with strict state machine validation.
     * Records lifecycle timestamps and a status log entry on each transition.
     */
    @Transactional
    public AppointmentResponse updateStatus(Long id, AppointmentStatus newStatus,
                                             String cancellationReason) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", id));
        AppointmentStatus current = appointment.getStatus();

        if (!current.canTransitionTo(newStatus)) {
            throw new InvalidStateTransitionException("appointment status",
                    current.name(), newStatus.name());
        }
        if (newStatus == AppointmentStatus.CANCELLED) {
            if (cancellationReason == null || cancellationReason.isBlank()) {
                throw new IllegalArgumentException("cancellationReason is required when cancelling");
            }
            appointment.setCancellationReason(cancellationReason);
        }

        LocalDateTime now = LocalDateTime.now();
        switch (newStatus) {
            // When moving back from IN_PROGRESS or RECALLED to CHECKED_IN,
            // preserve the original checkedInAt so the patient keeps their queue position.
            // Only set checkedInAt if it was never set (first check-in).
            case CHECKED_IN  -> {
                if (appointment.getCheckedInAt() == null) {
                    appointment.setCheckedInAt(now); // first check-in only
                }
                if (current == AppointmentStatus.COMPLETED) {
                    appointment.setConsultationStart(null);
                    appointment.setConsultationEnd(null);
                    appointment.setRecallCount(0);
                }
            }
            case IN_PROGRESS -> appointment.setConsultationStart(now);
            case COMPLETED   -> appointment.setConsultationEnd(now);
            case NO_SHOW     -> {
                appointment.setNoShowMarkedAt(now);
                // Clear heldAt when transitioning away from ON_HOLD
                if (current == AppointmentStatus.ON_HOLD) {
                    appointment.setHeldAt(null);
                }
            }
            case BOOKED      -> {
                // Skip-to-bottom: when transitioning from ON_HOLD to BOOKED, clear heldAt and overwrite checkedInAt
                if (current == AppointmentStatus.ON_HOLD) {
                    appointment.setHeldAt(null);
                    appointment.setCheckedInAt(now); // skip-to-bottom logic
                }
                if (current == AppointmentStatus.COMPLETED) {
                    appointment.setCheckedInAt(null);
                    appointment.setConsultationStart(null);
                    appointment.setConsultationEnd(null);
                    appointment.setRecallCount(0);
                }
            }
            default          -> {}
        }

        appointment.setStatus(newStatus);
        appointment = appointmentRepository.save(appointment);
        recordStatusLog(appointment, current, newStatus, getCurrentUser());
        
        // Broadcast the real-time update to receptionists/doctors
        try {
            Long tenantId = TenantContext.getTenantId();
            if (tenantId != null && appointment.getDoctor() != null) {
                queueEventService.broadcastQueueUpdate(tenantId, appointment.getDoctor().getId());
            }
        } catch (Exception e) {
            log.warn("Failed to broadcast queue update for appointment {}: {}", id, e.getMessage());
        }
        
        return mapToResponse(appointment);
    }

    @Transactional
    public AppointmentResponse recallToken(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", id));

        AppointmentStatus current = appointment.getStatus();
        if (current != AppointmentStatus.IN_PROGRESS && current != AppointmentStatus.RECALLED) {
            throw new InvalidStateTransitionException("appointment status", current.name(), "RECALLED");
        }

        int newRecallCount = (appointment.getRecallCount() == null ? 0 : appointment.getRecallCount()) + 1;
        appointment.setRecallCount(newRecallCount);
        appointment.setStatus(AppointmentStatus.RECALLED);
        appointment = appointmentRepository.save(appointment);

        recordStatusLog(appointment, current, AppointmentStatus.RECALLED, getCurrentUser());
        
        try {
            Long tenantId = TenantContext.getTenantId();
            if (tenantId != null && appointment.getDoctor() != null) {
                queueEventService.broadcastQueueUpdate(tenantId, appointment.getDoctor().getId());
            }
        } catch (Exception e) {}

        return mapToResponse(appointment);
    }

    @Transactional
    public AppointmentResponse holdAppointment(Long id) {
        Long tenantId = TenantContext.getTenantId();
        Appointment appointment = appointmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", id));

        AppointmentStatus current = appointment.getStatus();
        if (!current.canTransitionTo(AppointmentStatus.ON_HOLD)) {
            throw new InvalidStateTransitionException("appointment status",
                    current.name(), AppointmentStatus.ON_HOLD.name());
        }

        appointment.setStatus(AppointmentStatus.ON_HOLD);
        appointment.setHeldAt(LocalDateTime.now());
        appointment = appointmentRepository.save(appointment);
        recordStatusLog(appointment, current, AppointmentStatus.ON_HOLD, getCurrentUser());
        
        try {
            if (tenantId != null && appointment.getDoctor() != null) {
                queueEventService.broadcastQueueUpdate(tenantId, appointment.getDoctor().getId());
            }
        } catch (Exception e) {}

        return mapToResponse(appointment);
    }

    @Transactional
    public AppointmentResponse resumeAppointment(Long id) {
        Long tenantId = TenantContext.getTenantId();
        Appointment appointment = appointmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", id));

        AppointmentStatus current = appointment.getStatus();
        if (!current.canTransitionTo(AppointmentStatus.IN_PROGRESS)) {
            throw new InvalidStateTransitionException("appointment status",
                    current.name(), AppointmentStatus.IN_PROGRESS.name());
        }

        appointment.setStatus(AppointmentStatus.IN_PROGRESS);
        appointment.setHeldAt(null);
        appointment.setConsultationStart(LocalDateTime.now());
        appointment = appointmentRepository.save(appointment);
        recordStatusLog(appointment, current, AppointmentStatus.IN_PROGRESS, getCurrentUser());
        
        try {
            if (tenantId != null && appointment.getDoctor() != null) {
                queueEventService.broadcastQueueUpdate(tenantId, appointment.getDoctor().getId());
            }
        } catch (Exception e) {}

        return mapToResponse(appointment);
    }

    @Transactional
    public AppointmentResponse reassignDoctor(Long appointmentId, Long newDoctorId, String position) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));
        Doctor newDoctor = doctorRepository.findById(newDoctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", newDoctorId));
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();

        appointment.setDoctor(newDoctor);
        appointment.setReassignNeeded(false);

        if ("TOP".equalsIgnoreCase(position)) {
            // Find current minimum token for the new doctor today (Emergency placement at top of queue)
            Integer minToken = appointmentRepository.findMinTokenNumber(newDoctor.getId(), today, tenantId);
            int nextMin = (minToken != null) ? minToken - 1 : 0;
            if (nextMin > 0) nextMin = 0; // Emergency tokens are <= 0
            appointment.setTokenNumber(nextMin);
        } else {
            // Find current maximum token for the new doctor today (Standard placement at bottom of queue)
            Integer maxToken = appointmentRepository.findMaxTokenNumber(newDoctor.getId(), today, tenantId);
            int nextMax = (maxToken != null) ? maxToken + 1 : 1;
            if (nextMax < 1) nextMax = 1; // Standard tokens are >= 1
            appointment.setTokenNumber(nextMax);
        }

        appointment = appointmentRepository.save(appointment);

        try {
            queueEventService.broadcastQueueUpdate(tenantId, newDoctor.getId());
        } catch (Exception e) {}

        return mapToResponse(appointment);
    }

    public List<StatusLogResponse> getStatusLog(Long appointmentId) {
        return statusLogRepository.findByAppointmentIdOrderByChangedAtAsc(appointmentId)
                .stream()
                .map(l -> StatusLogResponse.builder()
                        .id(l.getId())
                        .previousStatus(l.getPreviousStatus())
                        .newStatus(l.getNewStatus())
                        .changedByName(l.getChangedBy().getFullName())
                        .changedAt(l.getChangedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private void recordStatusLog(Appointment appt, AppointmentStatus prev,
                                  AppointmentStatus next, User changedBy) {
        AppointmentStatusLog log = AppointmentStatusLog.builder()
                .appointment(appt).previousStatus(prev)
                .newStatus(next).changedBy(changedBy).build();
        statusLogRepository.save(log);
    }

    private List<String> generateTimeSlots() {
        List<String> slots = new ArrayList<>();
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(17, 0);

        while (start.isBefore(end)) {
            slots.add(start.toString());
            start = start.plusMinutes(30);
        }

        return slots;
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    /**
     * Encodes the counter label into the notes string as a prefix.
     * Format: "COUNTER:{X}|{original notes}"
     * Decoded in mapToResponse().
     */
    static String encodeCounter(String counter, String notes) {
        String base = notes != null ? notes : "";
        if (counter != null && !counter.isBlank()) {
            return "COUNTER:" + counter + "|" + base;
        }
        return base;
    }

    /**
     * Decodes the counter prefix from a notes string.
     * Returns [counter, originalNotes] — counter may be null.
     */
    static String[] decodeCounter(String notes) {
        if (notes != null && notes.startsWith("COUNTER:")) {
            int sep = notes.indexOf('|');
            if (sep > 8) {
                return new String[]{ notes.substring(8, sep), notes.substring(sep + 1) };
            }
        }
        return new String[]{ null, notes };
    }

    private AppointmentResponse mapToResponse(Appointment appointment) {
        // ── Decode counter from notes prefix ──────────────────────────────────
        String counter = null;
        String displayNotes = appointment.getNotes();
        if (displayNotes != null && displayNotes.startsWith("COUNTER:")) {
            int sep = displayNotes.indexOf('|');
            if (sep > 8) {
                counter = displayNotes.substring(8, sep);
                displayNotes = displayNotes.substring(sep + 1);
            }
        }

        // ── Active queue length for this doctor today ─────────────────────────
        Integer activeQueueLength = null;
        try {
            Long tenantId = TenantContext.getTenantId();
            if (tenantId != null && appointment.getDoctor() != null) {
                activeQueueLength = (int) appointmentRepository.countActiveByDoctorAndDate(
                        appointment.getDoctor().getId(), LocalDate.now(), tenantId);
            }
        } catch (Exception e) {
            log.warn("Could not compute activeQueueLength: {}", e.getMessage());
        }

        return AppointmentResponse.builder()
                .id(appointment.getId())
                .patientId(appointment.getPatient().getId())
                .patientName(appointment.getPatient().getFirstName() + " " + appointment.getPatient().getLastName())
                .patientCode(appointment.getPatient().getPatientCode())
                .doctorId(appointment.getDoctor().getId())
                .doctorName(appointment.getDoctor().getUser().getFullName())
                .doctorQualification(appointment.getDoctor().getQualification())
                .department(appointment.getDoctor().getDepartment() != null ?
                        appointment.getDoctor().getDepartment().getName() : null)
                .appointmentDate(appointment.getAppointmentDate())
                .appointmentTime(appointment.getAppointmentTime())
                .type(appointment.getType())
                .tokenNumber(appointment.getTokenNumber())
                .status(appointment.getStatus())
                .notes(displayNotes)
                .counter(counter)
                .activeQueueLength(activeQueueLength)
                .cancellationReason(appointment.getCancellationReason())
                .checkedInAt(appointment.getCheckedInAt())
                .consultationStart(appointment.getConsultationStart())
                .consultationEnd(appointment.getConsultationEnd())
                .noShowMarkedAt(appointment.getNoShowMarkedAt())
                .heldAt(appointment.getHeldAt())
                .createdAt(appointment.getCreatedAt())
                .recallCount(appointment.getRecallCount())
                .reassignNeeded(appointment.getReassignNeeded())
                .build();
    }
}
