package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.DoctorAvailabilityRequest;
import com.curamatrix.hsm.dto.request.DoctorStatusUpdateRequest;
import com.curamatrix.hsm.dto.response.DoctorAvailabilityResponse;
import com.curamatrix.hsm.entity.Doctor;
import com.curamatrix.hsm.entity.DoctorAvailability;
import com.curamatrix.hsm.entity.User;
import com.curamatrix.hsm.entity.DoctorStatusLog;
import com.curamatrix.hsm.entity.Appointment;
import com.curamatrix.hsm.entity.AppointmentStatusLog;
import com.curamatrix.hsm.enums.DoctorStatus;
import com.curamatrix.hsm.enums.AppointmentStatus;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.DoctorAvailabilityRepository;
import com.curamatrix.hsm.repository.DoctorRepository;
import com.curamatrix.hsm.repository.UserRepository;
import com.curamatrix.hsm.repository.DoctorStatusLogRepository;
import com.curamatrix.hsm.repository.AppointmentRepository;
import com.curamatrix.hsm.repository.AppointmentStatusLogRepository;
import com.curamatrix.hsm.service.QueueEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DoctorAvailabilityService {

    private final DoctorAvailabilityRepository availabilityRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final DoctorStatusLogRepository doctorStatusLogRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusLogRepository appointmentStatusLogRepository;
    private final QueueEventService queueEventService;

    public DoctorAvailabilityResponse getAvailabilityByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return getAvailabilityForUserId(user.getId());
    }

    public DoctorAvailabilityResponse getAvailabilityForUserId(Long userId) {
        Doctor doctor = doctorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "userId", userId));
        return getAvailability(doctor.getId(), LocalDate.now());
    }

    /** Get today's availability for a single doctor */
    @Transactional(readOnly = true)
    public DoctorAvailabilityResponse getAvailability(Long doctorId, LocalDate date) {
        Long tenantId = TenantContext.getTenantId();
        Optional<DoctorAvailability> record = availabilityRepository
                .findByDoctorIdAndAvailabilityDateAndTenantId(doctorId, date, tenantId);
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", doctorId));
        // Default: present and AVAILABLE when no record exists
        return record.map(r -> toResponse(r, doctor))
                .orElseGet(() -> DoctorAvailabilityResponse.builder()
                        .userId(doctor.getUser().getId())
                        .doctorId(doctorId)
                        .doctorName(doctor.getUser().getFullName())
                        .departmentName(doctor.getDepartment() != null ? doctor.getDepartment().getName() : null)
                        .date(date)
                        .isPresent(false)
                        .status(DoctorStatus.OFFLINE)
                        .build());
    }

    /** Get today's availability for all doctors in a tenant */
    @Transactional(readOnly = true)
    public List<DoctorAvailabilityResponse> getTodayAvailability() {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();
        List<Doctor> allDoctors = doctorRepository.findByTenantId(tenantId);
        return allDoctors.stream()
                .filter(doctor -> doctor.getUser() != null)  // skip doctors with broken user association
                .map(doctor -> {
                    Optional<DoctorAvailability> record = availabilityRepository
                            .findByDoctorIdAndAvailabilityDateAndTenantId(doctor.getId(), today, tenantId);
                    return record.map(r -> toResponse(r, doctor))
                            .orElseGet(() -> DoctorAvailabilityResponse.builder()
                                    .userId(doctor.getUser().getId())
                                    .doctorId(doctor.getId())
                                    .doctorName(doctor.getUser().getFullName())
                                    .departmentName(doctor.getDepartment() != null ? doctor.getDepartment().getName() : null)
                                    .date(today)
                                    .isPresent(false)
                                    .status(DoctorStatus.OFFLINE)
                                    .build());
                }).collect(Collectors.toList());
    }

    /** Upsert presence + duty hours (used by admin for scheduling) */
    public DoctorAvailabilityResponse upsertAvailability(Long doctorId, DoctorAvailabilityRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", doctorId));
        DoctorAvailability record = availabilityRepository
                .findByDoctorIdAndAvailabilityDateAndTenantId(doctorId, request.getDate(), tenantId)
                .orElseGet(() -> DoctorAvailability.builder()
                        .doctor(doctor).availabilityDate(request.getDate()).build());
        record.setIsPresent(request.getIsPresent());
        if (!request.getIsPresent()) {
            record.setStatus(DoctorStatus.OFFLINE);
        } else if (record.getStatus() == DoctorStatus.OFFLINE) {
            record.setStatus(DoctorStatus.AVAILABLE);
        }
        record = availabilityRepository.save(record);
        return toResponse(record, doctor);
    }

    /** Update real-time status (used by receptionist or doctor during the day) */
    public DoctorAvailabilityResponse updateStatus(Long doctorId, DoctorStatusUpdateRequest request) {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", doctorId));
        DoctorAvailability record = availabilityRepository
                .findByDoctorIdAndAvailabilityDateAndTenantId(doctorId, today, tenantId)
                .orElseGet(() -> DoctorAvailability.builder()
                        .doctor(doctor).availabilityDate(today).isPresent(true).build());

        DoctorStatus oldStatus = record.getStatus();
        DoctorStatus newStatus = request.getStatus();

        record.setStatus(newStatus);
        String note = request.getReason() != null ? request.getReason() : request.getStatusNote();
        record.setStatusNote(note);
        record.setAvailableFrom(request.getAvailableFrom());
        if (request.getDutyStart() != null) record.setDutyStart(request.getDutyStart());
        if (request.getDutyEnd() != null) record.setDutyEnd(request.getDutyEnd());

        // Sync isPresent with status
        record.setIsPresent(newStatus != DoctorStatus.OFFLINE);

        record = availabilityRepository.save(record);

        // 1. Create audit log
        User currentUser = getCurrentUser();
        DoctorStatusLog logRecord = DoctorStatusLog.builder()
                .doctor(doctor)
                .previousStatus(oldStatus)
                .newStatus(newStatus)
                .changedBy(currentUser)
                .build();
        doctorStatusLogRepository.save(logRecord);

        // 2. Offline / Away / Emergency / Procedure mid-queue logic
        if (newStatus != DoctorStatus.AVAILABLE) {
            List<Appointment> activeAppts = appointmentRepository.findActiveByDoctorAndDateAndTenant(doctorId, today, tenantId);
            for (Appointment appt : activeAppts) {
                AppointmentStatus currentApptStatus = appt.getStatus();
                
                appt.setReassignNeeded(true);

                if (currentApptStatus == AppointmentStatus.IN_PROGRESS || currentApptStatus == AppointmentStatus.RECALLED) {
                    appt.setStatus(AppointmentStatus.CHECKED_IN);
                    appt.setConsultationStart(null);
                    appt.setConsultationEnd(null);

                    AppointmentStatusLog apptStatusLog = AppointmentStatusLog.builder()
                            .appointment(appt)
                            .previousStatus(currentApptStatus)
                            .newStatus(AppointmentStatus.CHECKED_IN)
                            .changedBy(currentUser)
                            .build();
                    appointmentStatusLogRepository.save(apptStatusLog);
                }
                appointmentRepository.save(appt);
            }
        } else {
            // When returning to AVAILABLE, clear the reassignment flag for remaining active appointments
            List<Appointment> activeAppts = appointmentRepository.findActiveByDoctorAndDateAndTenant(doctorId, today, tenantId);
            for (Appointment appt : activeAppts) {
                if (appt.getReassignNeeded()) {
                    appt.setReassignNeeded(false);
                    appointmentRepository.save(appt);
                }
            }
        }

        // 3. Broadcast queue update via SSE
        try {
            queueEventService.broadcastQueueUpdate(tenantId, doctorId);
        } catch (Exception e) {
            log.warn("Failed to broadcast queue update for doctor status change: {}", e.getMessage());
        }

        log.info("Doctor {} status updated to {} by {}", doctorId, newStatus, currentUser.getEmail());
        return toResponse(record, doctor);
    }

    public void verifyDoctorSelfUpdate(Long doctorId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        Doctor doctor = doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Only doctors can update their own status"));
        if (!doctor.getId().equals(doctorId)) {
            throw new org.springframework.security.access.AccessDeniedException("You can only update your own availability status");
        }
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private DoctorAvailabilityResponse toResponse(DoctorAvailability r, Doctor doctor) {
        User user = doctor.getUser();
        return DoctorAvailabilityResponse.builder()
                .userId(user != null ? user.getId() : null)
                .doctorId(r.getDoctor().getId())
                .doctorName(user != null ? user.getFullName() : "Unknown Doctor")
                .departmentName(doctor.getDepartment() != null ? doctor.getDepartment().getName() : null)
                .date(r.getAvailabilityDate())
                .isPresent(r.getIsPresent())
                .status(r.getStatus())
                .statusNote(r.getStatusNote())
                .availableFrom(r.getAvailableFrom())
                .dutyStart(r.getDutyStart())
                .dutyEnd(r.getDutyEnd())
                .build();
    }
}
