package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.PatientRequest;
import com.curamatrix.hsm.dto.response.DuplicateCheckResponse;
import com.curamatrix.hsm.dto.response.PatientResponse;
import com.curamatrix.hsm.dto.response.PatientSummaryResponse;
import com.curamatrix.hsm.dto.response.PatientVisitHistoryResponse;
import com.curamatrix.hsm.entity.Patient;
import com.curamatrix.hsm.entity.PatientRegistration;
import com.curamatrix.hsm.entity.Tenant;
import com.curamatrix.hsm.entity.User;
import com.curamatrix.hsm.enums.SubscriptionPlan;
import com.curamatrix.hsm.enums.AppointmentStatus;
import com.curamatrix.hsm.exception.DuplicateResourceException;
import com.curamatrix.hsm.exception.QuotaExceededException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.entity.Appointment;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRegistrationRepository patientRegistrationRepository;
    private final DoctorAvailabilityService doctorAvailabilityService;

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

        // ─── Duplicate detection handled via explicit /check-duplicate API ─
        // We removed the strict exception here to allow receptionists to manually bypass 
        // the warning if they confirm it is a different person.

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
                .guardianName(request.getGuardianName())
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
        patient.setGuardianName(request.getGuardianName());
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

        // Update the doctor's global status to IN_CONSULTATION
        try {
            com.curamatrix.hsm.dto.request.DoctorStatusUpdateRequest statusReq = 
                    new com.curamatrix.hsm.dto.request.DoctorStatusUpdateRequest();
            statusReq.setStatus(com.curamatrix.hsm.enums.DoctorStatus.IN_CONSULTATION);
            doctorAvailabilityService.updateStatus(doctor.getId(), statusReq);
        } catch (Exception e) {
            log.warn("Could not update doctor status to IN_CONSULTATION on patient checkin: {}", e.getMessage());
        }

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
            
            // Revert doctor status to ON_DUTY if this was their last IN_PROGRESS appointment
            try {
                Long remainingInProgress = appointmentRepository.countOtherInProgressByDoctor(
                        appt.getDoctor().getId(), LocalDate.now(), appt.getTenantId(), appt.getId());
                if (remainingInProgress == 0) {
                    com.curamatrix.hsm.dto.request.DoctorStatusUpdateRequest statusReq = 
                            new com.curamatrix.hsm.dto.request.DoctorStatusUpdateRequest();
                    statusReq.setStatus(com.curamatrix.hsm.enums.DoctorStatus.ON_DUTY);
                    doctorAvailabilityService.updateStatus(appt.getDoctor().getId(), statusReq);
                }
            } catch (Exception e) {
                log.warn("Could not revert doctor status on checkout: {}", e.getMessage());
            }
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

    /**
     * Search patients by name / phone / patientCode and enrich each result with
     * inline case paper status and last visit date.
     * Used by GET /api/patients/search?q= (reception desk search bar).
     */
    @Transactional(readOnly = true)
    public List<PatientSummaryResponse> searchPatientsWithCasePaper(String q, Long tenantId) {
        if (q == null || q.trim().length() < 2) {
            return new ArrayList<>();
        }
        // Reuse existing tenant-scoped search (page 0, size 20)
        List<Patient> patients = patientRepository
                .searchByTenant(q.trim(), tenantId, PageRequest.of(0, 20))
                .getContent();

        LocalDateTime now = LocalDateTime.now();
        List<PatientSummaryResponse> results = new ArrayList<>();

        for (Patient p : patients) {
            // Case paper status — backend authoritative
            Optional<PatientRegistration> regOpt = patientRegistrationRepository
                    .findFirstByPatientIdAndTenantIdAndActiveTrueOrderByExpiresAtDesc(p.getId(), tenantId);

            PatientSummaryResponse.CasePaperSummary casePaper;
            if (regOpt.isEmpty()) {
                casePaper = PatientSummaryResponse.CasePaperSummary.builder()
                        .valid(false).remainingDays(-1).expiringSoon(false).build();
            } else {
                PatientRegistration reg = regOpt.get();
                boolean valid = !reg.isExpired();
                long remaining = valid ? ChronoUnit.DAYS.between(now, reg.getExpiresAt()) : -1;
                casePaper = PatientSummaryResponse.CasePaperSummary.builder()
                        .valid(valid)
                        .expiresAt(reg.getExpiresAt())
                        .remainingDays(remaining)
                        .expiringSoon(valid && remaining <= 5)
                        .build();
            }

            // Last visit date
            LocalDate lastVisitDate = null;
            try {
                List<com.curamatrix.hsm.entity.Appointment> recent =
                        appointmentRepository.findTopByPatientIdAndTenantIdOrderByAppointmentDateDesc(
                                p.getId(), tenantId, PageRequest.of(0, 1));
                if (!recent.isEmpty()) {
                    lastVisitDate = recent.get(0).getAppointmentDate();
                }
            } catch (Exception e) {
                log.warn("Could not fetch last visit for patient {}: {}", p.getId(), e.getMessage());
            }

            results.add(PatientSummaryResponse.builder()
                    .id(p.getId())
                    .patientCode(p.getPatientCode())
                    .firstName(p.getFirstName())
                    .lastName(p.getLastName())
                    .phone(p.getPhone())
                    .dateOfBirth(p.getDateOfBirth() != null ? p.getDateOfBirth().toString() : null)
                    .gender(p.getGender() != null ? p.getGender().name() : null)
                    .lastVisitDate(lastVisitDate)
                    .casePaper(casePaper)
                    .build());
        }
        return results;
    }

    public DuplicateCheckResponse checkDuplicate(PatientRequest request) {
        Long tenantId = TenantContext.getTenantId();

        if (request.getPhone() == null || request.getPhone().isBlank()) {
            return DuplicateCheckResponse.builder().exists(false).build();
        }

        // Check by phone number only — the receptionist verifies other details orally
        List<Patient> duplicates = patientRepository.findByPhoneAndTenantId(
                request.getPhone().trim(), tenantId);

        if (duplicates.isEmpty()) {
            return DuplicateCheckResponse.builder().exists(false).build();
        }

        // Return ALL patients with this phone number so receptionist can verify
        List<PatientResponse> patientResponses = duplicates.stream()
                .map(patient -> {
                    Optional<com.curamatrix.hsm.entity.PatientRegistration> reg =
                            patientRegistrationRepository.findFirstByPatientIdAndTenantIdAndActiveTrueOrderByExpiresAtDesc(
                                    patient.getId(), tenantId);
                    
                    boolean isValid = reg.isPresent() && !reg.get().isExpired();
                    java.time.LocalDateTime expiresAt = reg.map(com.curamatrix.hsm.entity.PatientRegistration::getExpiresAt).orElse(null);
                    
                    PatientResponse response = mapToResponse(patient);
                    // Add case paper validity info to each patient
                    response.setCasePaperValid(isValid);
                    response.setCasePaperExpiresAt(expiresAt != null ? expiresAt.toString() : null);
                    return response;
                })
                .toList();

        // For backward compatibility, set isCasePaperValid based on the most recent patient
        Patient mostRecent = duplicates.get(0);
        Optional<com.curamatrix.hsm.entity.PatientRegistration> mostRecentReg =
                patientRegistrationRepository.findFirstByPatientIdAndTenantIdAndActiveTrueOrderByExpiresAtDesc(
                        mostRecent.getId(), tenantId);
        boolean isValid = mostRecentReg.isPresent() && !mostRecentReg.get().isExpired();
        java.time.LocalDateTime expiresAt = mostRecentReg.map(com.curamatrix.hsm.entity.PatientRegistration::getExpiresAt).orElse(null);

        return DuplicateCheckResponse.builder()
                .exists(true)
                .patients(patientResponses)
                .isCasePaperValid(isValid)
                .expiresAt(expiresAt != null ? expiresAt.toString() : null)
                .build();
    }

    private PatientResponse mapToResponse(Patient patient) {
        PatientResponse resp = PatientResponse.builder()
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
                .guardianName(patient.getGuardianName())
                .allergies(patient.getAllergies())
                .medicalHistory(patient.getMedicalHistory())
                .registeredAt(patient.getRegisteredAt())
                .checkedIn(patient.getCheckedIn())
                .checkedOut(patient.getCheckedOut())
                .build();
                
        // Inject active appointment info for today's visits (Booked, Checked-In, or In-Progress)
        try {
            LocalDate today = LocalDate.now();
            appointmentRepository.findByFilters(
                today, null, patient.getId(), null, null, org.springframework.data.domain.PageRequest.of(0, 10))
            .getContent().stream()
            .filter(a -> a.getStatus() == AppointmentStatus.BOOKED || 
                         a.getStatus() == AppointmentStatus.CHECKED_IN || 
                         a.getStatus() == AppointmentStatus.IN_PROGRESS)
            .findFirst()
            .ifPresent(appt -> {
                resp.setActiveAppointmentId(appt.getId());
                resp.setActiveAppointmentStatus(appt.getStatus().name());
                resp.setActiveTokenNumber(appt.getTokenNumber());
                if (appt.getDoctor() != null) {
                    resp.setActiveAppointmentDoctorId(appt.getDoctor().getId());
                    resp.setActiveAppointmentDoctorName(appt.getDoctor().getUser().getFullName());
                }
            });
        } catch (Exception e) {
            log.warn("Could not fetch active appointment for patient: {}", e.getMessage());
        }
        return resp;
    }
}
