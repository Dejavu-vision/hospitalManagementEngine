package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.PatientRequest;
import com.curamatrix.hsm.dto.response.PatientResponse;
import com.curamatrix.hsm.dto.response.PatientVisitHistoryResponse;
import com.curamatrix.hsm.entity.Patient;
import com.curamatrix.hsm.entity.Tenant;
import com.curamatrix.hsm.entity.User;
import com.curamatrix.hsm.enums.SubscriptionPlan;
import com.curamatrix.hsm.enums.AppointmentStatus;
import com.curamatrix.hsm.exception.DuplicateResourceException;
import com.curamatrix.hsm.exception.QuotaExceededException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.entity.Appointment;
import com.curamatrix.hsm.repository.PatientRepository;
import com.curamatrix.hsm.repository.TenantRepository;
import com.curamatrix.hsm.repository.UserRepository;
import com.curamatrix.hsm.repository.AppointmentRepository;
import com.curamatrix.hsm.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;

    @Transactional
    public PatientResponse registerPatient(PatientRequest request) {
        String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User registeredBy = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", currentUserEmail));

        Long tenantId = registeredBy.getTenantId();
        if (tenantId == null) {
            tenantId = TenantContext.getTenantId();
        }
        final Long effectiveTenantId = tenantId;

        // ─── Quota enforcement: check patient limit ────────────────
        if (effectiveTenantId != null) {
            Tenant tenant = tenantRepository.findById(effectiveTenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", effectiveTenantId));

            SubscriptionPlan plan = SubscriptionPlan.valueOf(tenant.getSubscriptionPlan());
            if (!plan.isUnlimited("patients")) {
                long currentPatients = patientRepository.countByTenantId(effectiveTenantId);
                if (currentPatients >= tenant.getMaxPatients()) {
                    throw new QuotaExceededException(
                            "Patient registration limit reached. Your plan allows a maximum of " +
                            tenant.getMaxPatients() + " patients. " +
                            "Please upgrade your subscription to register more patients.");
                }
            }
        }

        // ─── Duplicate detection: warn when name + DOB match exists ─
        if (effectiveTenantId != null && request.getFirstName() != null && request.getLastName() != null
                && request.getDateOfBirth() != null) {
            boolean duplicateExists = patientRepository
                    .existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndDateOfBirthAndTenantId(
                            request.getFirstName(), request.getLastName(),
                            request.getDateOfBirth(), effectiveTenantId);
            if (duplicateExists) {
                throw new DuplicateResourceException(
                        "A patient with the same name and date of birth already exists: " +
                        request.getFirstName() + " " + request.getLastName() +
                        " (DOB: " + request.getDateOfBirth() + "). " +
                        "Please verify this is not a duplicate registration.");
            }
        }

        // Generate human-readable patient code before saving
        // Format: P{YY}{4-digit-sequence} e.g. P260001 — short, readable, fits on token slips
        String yy = String.valueOf(java.time.Year.now().getValue()).substring(2); // "26"
        long count = patientRepository.countByTenantId(effectiveTenantId != null ? effectiveTenantId : 0L) + 1;
        String patientCode = "P" + yy + String.format("%04d", count);

        Patient patient = Patient.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .phone(request.getPhone())
                .email(request.getEmail())
                .address(request.getAddress())
                .bloodGroup(request.getBloodGroup())
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .allergies(request.getAllergies())
                .medicalHistory(request.getMedicalHistory())
                .registeredBy(registeredBy)
                .patientCode(patientCode)
                .build();

        patient = patientRepository.save(patient);
        log.info("Patient registered: {} {} [{}]", patient.getFirstName(), patient.getLastName(), patient.getPatientCode());

        return mapToResponse(patient);
    }

    public Page<PatientResponse> searchPatients(String search, String gender, String bloodGroup, Pageable pageable) {
        Long tenantId = TenantContext.getTenantId();
        // Use the filtered query
        return patientRepository.searchWithFilters(
                (search != null && search.trim().length() >= 2) ? search.trim() : null,
                gender,
                bloodGroup,
                tenantId,
                pageable
        ).map(this::mapToResponse);
    }

    public PatientResponse getPatientById(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id));
        return mapToResponse(patient);
    }

    @Transactional
    public PatientResponse updatePatient(Long id, PatientRequest request) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id));

        patient.setFirstName(request.getFirstName());
        patient.setLastName(request.getLastName());
        patient.setDateOfBirth(request.getDateOfBirth());
        patient.setGender(request.getGender());
        patient.setPhone(request.getPhone());
        patient.setEmail(request.getEmail());
        patient.setAddress(request.getAddress());
        patient.setBloodGroup(request.getBloodGroup());
        patient.setEmergencyContactName(request.getEmergencyContactName());
        patient.setEmergencyContactPhone(request.getEmergencyContactPhone());
        patient.setAllergies(request.getAllergies());
        patient.setMedicalHistory(request.getMedicalHistory());

        patient = patientRepository.save(patient);
        log.info("Patient updated: {}", id);

        return mapToResponse(patient);
    }

    @Transactional
    public PatientResponse checkInPatient(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id));
        patient.setCheckedIn(true);
        // Usually checkedOut is reset when checked in for a new visit
        patient.setCheckedOut(false);
        patient = patientRepository.save(patient);
        log.info("Patient checked in: {}", id);

        // Auto-create an IN_PROGRESS appointment
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        
        com.curamatrix.hsm.entity.Doctor doctor = doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new com.curamatrix.hsm.exception.InvalidStateTransitionException(
                        "Check-in failed: Your user account is not linked to a Doctor profile. " +
                        "Only full Doctors can execute direct check-ins."
                ));

        Appointment appt = Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .bookedBy(user)
                .appointmentDate(java.time.LocalDate.now())
                .appointmentTime(java.time.LocalTime.now())
                .type(com.curamatrix.hsm.enums.AppointmentType.WALK_IN)
                .status(com.curamatrix.hsm.enums.AppointmentStatus.IN_PROGRESS)
                .consultationStart(java.time.LocalDateTime.now())
                .build();
        
        appt = appointmentRepository.save(appt);
        Long activeAppointmentId = appt.getId();

        PatientResponse resp = mapToResponse(patient);
        resp.setActiveAppointmentId(activeAppointmentId);
        return resp;
    }

    @Transactional
    public PatientResponse checkOutPatient(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id));
        
        // Fulfill user request: checkin should be false
        patient.setCheckedIn(false);
        patient.setCheckedOut(true);
        patient = patientRepository.save(patient);

        // Also complete any IN_PROGRESS appointments for this patient
        // We use the existing filter query pattern
        List<Appointment> inProgressAppts = appointmentRepository.findByFilters(
                null, null, id, AppointmentStatus.IN_PROGRESS, null, org.springframework.data.domain.PageRequest.of(0, 10)
        ).getContent();

        for (Appointment appt : inProgressAppts) {
            appt.setStatus(AppointmentStatus.COMPLETED);
            appt.setConsultationEnd(java.time.LocalDateTime.now());
            appointmentRepository.save(appt);
        }

        log.info("Patient checked out and {} active appointments completed: {}", inProgressAppts.size(), id);
        return mapToResponse(patient);
    }

    public PatientVisitHistoryResponse getVisitHistory(Long patientId) {
        Long tenantId = TenantContext.getTenantId();
        patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId));
        // findVisitSummaryByPatient returns List<Object[]> — get the first row
        java.util.List<Object[]> rows = appointmentRepository.findVisitSummaryByPatient(patientId, tenantId);
        long totalVisits = 0L;
        LocalDate lastVisit = null;
        if (rows != null && !rows.isEmpty()) {
            Object[] row = rows.get(0);
            totalVisits = row[0] != null ? ((Number) row[0]).longValue() : 0L;
            if (row[1] != null) {
                if (row[1] instanceof java.sql.Date) {
                    lastVisit = ((java.sql.Date) row[1]).toLocalDate();
                } else if (row[1] instanceof LocalDate) {
                    lastVisit = (LocalDate) row[1];
                }
            }
        }
        return PatientVisitHistoryResponse.builder()
                .patientId(patientId).totalVisits(totalVisits).lastVisitDate(lastVisit).build();
    }

    private PatientResponse mapToResponse(Patient patient) {
        return PatientResponse.builder()
                .id(patient.getId())
                .patientCode(patient.getPatientCode())
                .firstName(patient.getFirstName())
                .lastName(patient.getLastName())
                .dateOfBirth(patient.getDateOfBirth())
                .gender(patient.getGender())
                .phone(patient.getPhone())
                .email(patient.getEmail())
                .address(patient.getAddress())
                .bloodGroup(patient.getBloodGroup())
                .emergencyContactName(patient.getEmergencyContactName())
                .emergencyContactPhone(patient.getEmergencyContactPhone())
                .allergies(patient.getAllergies())
                .medicalHistory(patient.getMedicalHistory())
                .registeredAt(patient.getRegisteredAt())
                .checkedIn(patient.getCheckedIn())
                .checkedOut(patient.getCheckedOut())
                .build();
    }
}
