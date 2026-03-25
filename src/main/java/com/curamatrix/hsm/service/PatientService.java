package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.PatientRequest;
import com.curamatrix.hsm.dto.response.PatientResponse;
import com.curamatrix.hsm.entity.Patient;
import com.curamatrix.hsm.entity.Tenant;
import com.curamatrix.hsm.entity.User;
import com.curamatrix.hsm.enums.SubscriptionPlan;
import com.curamatrix.hsm.exception.DuplicateResourceException;
import com.curamatrix.hsm.exception.QuotaExceededException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.PatientRepository;
import com.curamatrix.hsm.repository.TenantRepository;
import com.curamatrix.hsm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

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
                .build();

        patient = patientRepository.save(patient);
        log.info("Patient registered: {} {}", patient.getFirstName(), patient.getLastName());

        return mapToResponse(patient);
    }

    public Page<PatientResponse> searchPatients(String search, Pageable pageable) {
        if (search == null || search.trim().isEmpty()) {
            return patientRepository.findAll(pageable).map(this::mapToResponse);
        }
        return patientRepository.searchPatients(search.trim(), pageable).map(this::mapToResponse);
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

    private PatientResponse mapToResponse(Patient patient) {
        return PatientResponse.builder()
                .id(patient.getId())
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
                .build();
    }
}
