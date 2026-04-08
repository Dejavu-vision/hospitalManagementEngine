package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.AppointmentRequest;
import com.curamatrix.hsm.dto.request.WalkInRequest;
import com.curamatrix.hsm.dto.response.AppointmentResponse;
import com.curamatrix.hsm.dto.response.SlotResponse;
import com.curamatrix.hsm.dto.response.StatusLogResponse;
import com.curamatrix.hsm.entity.Appointment;
import com.curamatrix.hsm.entity.AppointmentStatusLog;
import com.curamatrix.hsm.entity.Doctor;
import com.curamatrix.hsm.entity.Patient;
import com.curamatrix.hsm.entity.User;
import com.curamatrix.hsm.entity.WalkInTokenSequence;
import com.curamatrix.hsm.enums.AppointmentStatus;
import com.curamatrix.hsm.enums.AppointmentType;
import com.curamatrix.hsm.exception.DuplicateResourceException;
import com.curamatrix.hsm.exception.InvalidStateTransitionException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.AppointmentRepository;
import com.curamatrix.hsm.repository.AppointmentStatusLogRepository;
import com.curamatrix.hsm.repository.DoctorRepository;
import com.curamatrix.hsm.repository.PatientRepository;
import com.curamatrix.hsm.repository.UserRepository;
import com.curamatrix.hsm.repository.WalkInTokenSequenceRepository;
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
        billingService.createAppointmentBilling(appointment, request.isPayNow());

        return mapToResponse(appointment);
    }

    @Transactional
    public AppointmentResponse createWalkIn(WalkInRequest request) {
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", request.getPatientId()));
        Doctor doctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", request.getDoctorId()));
        User bookedBy = getCurrentUser();
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();

        // Atomic token increment — one sequence per (date, tenant) across ALL doctors
        WalkInTokenSequence seq = tokenSequenceRepository
                .findForUpdate(today, tenantId)
                .orElseGet(() -> WalkInTokenSequence.builder()
                        .appointmentDate(today).lastToken(0).build());
        seq.setLastToken(seq.getLastToken() + 1);
        seq = tokenSequenceRepository.save(seq);
        int nextToken = seq.getLastToken();

        Appointment appointment = Appointment.builder()
                .patient(patient).doctor(doctor).bookedBy(bookedBy)
                .appointmentDate(today).type(AppointmentType.WALK_IN)
                .tokenNumber(nextToken).status(AppointmentStatus.BOOKED)
                .notes(request.getNotes()).build();
        appointment = appointmentRepository.save(appointment);

        // Create billing
        billingService.createAppointmentBilling(appointment, request.isPayNow());

        recordStatusLog(appointment, null, AppointmentStatus.BOOKED, bookedBy);
        return mapToResponse(appointment);
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
            case CHECKED_IN  -> appointment.setCheckedInAt(now);
            case IN_PROGRESS -> appointment.setConsultationStart(now);
            case COMPLETED   -> appointment.setConsultationEnd(now);
            case NO_SHOW     -> appointment.setNoShowMarkedAt(now);
            default          -> {}
        }

        appointment.setStatus(newStatus);
        appointment = appointmentRepository.save(appointment);
        recordStatusLog(appointment, current, newStatus, getCurrentUser());
        return mapToResponse(appointment);
    }

    @Transactional
    public AppointmentResponse reassignDoctor(Long appointmentId, Long newDoctorId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));
        Doctor newDoctor = doctorRepository.findById(newDoctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", newDoctorId));
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();

        // Reassign keeps the existing token — no new token needed, patient keeps their place
        appointment.setDoctor(newDoctor);
        appointment = appointmentRepository.save(appointment);
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

    private AppointmentResponse mapToResponse(Appointment appointment) {
        return AppointmentResponse.builder()
                .id(appointment.getId())
                .patientId(appointment.getPatient().getId())
                .patientName(appointment.getPatient().getFirstName() + " " + appointment.getPatient().getLastName())
                .patientCode(appointment.getPatient().getPatientCode())
                .doctorId(appointment.getDoctor().getId())
                .doctorName(appointment.getDoctor().getUser().getFullName())
                .department(appointment.getDoctor().getDepartment() != null ?
                        appointment.getDoctor().getDepartment().getName() : null)
                .appointmentDate(appointment.getAppointmentDate())
                .appointmentTime(appointment.getAppointmentTime())
                .type(appointment.getType())
                .tokenNumber(appointment.getTokenNumber())
                .status(appointment.getStatus())
                .notes(appointment.getNotes())
                .cancellationReason(appointment.getCancellationReason())
                .checkedInAt(appointment.getCheckedInAt())
                .consultationStart(appointment.getConsultationStart())
                .consultationEnd(appointment.getConsultationEnd())
                .noShowMarkedAt(appointment.getNoShowMarkedAt())
                .createdAt(appointment.getCreatedAt())
                .build();
    }
}
