package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.entity.Patient;
import com.curamatrix.hsm.entity.PatientRegistration;
import com.curamatrix.hsm.repository.PatientRegistrationRepository;
import com.curamatrix.hsm.repository.PatientRepository;
import com.curamatrix.hsm.repository.TenantRepository;
import com.curamatrix.hsm.entity.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/case-paper")
@RequiredArgsConstructor
public class CasePaperController {

    private final PatientRepository patientRepository;
    private final PatientRegistrationRepository registrationRepository;
    private final TenantRepository tenantRepository;

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<?> getCasePaperData(
            @PathVariable Long patientId,
            @RequestParam(required = false) Long registrationId) {
        Long tenantId = TenantContext.getTenantId();
        Patient patient = patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        PatientRegistration registration;
        if (registrationId != null) {
            registration = registrationRepository.findById(registrationId)
                    .filter(r -> r.getPatient().getId().equals(patientId) && r.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new RuntimeException("Case paper not found"));
        } else {
            registration = registrationRepository.findLatestActiveRegistration(patientId, tenantId)
                    .orElse(null);
        }

        Map<String, Object> regDto = null;
        if (registration != null) {
            regDto = new HashMap<>();
            regDto.put("id", registration.getId());
            regDto.put("issuedAt", registration.getIssuedAt() != null ? registration.getIssuedAt().toString() : null);
            regDto.put("expiresAt", registration.getExpiresAt() != null ? registration.getExpiresAt().toString() : null);
            regDto.put("active", registration.isActive());
            regDto.put("notes", registration.getNotes());
        }

        Map<String, Object> hospitalMap = null;
        Optional<Tenant> tenantOpt = tenantRepository.findById(tenantId);
        if (tenantOpt.isPresent()) {
            Tenant tenant = tenantOpt.get();
            hospitalMap = new HashMap<>();
            hospitalMap.put("name", tenant.getHospitalName());
            hospitalMap.put("logo", tenant.getLogo());
            hospitalMap.put("address", tenant.getAddress());
        }

        Map<String, Object> patientDto = new HashMap<>();
        patientDto.put("id", patient.getId());
        patientDto.put("firstName", patient.getFirstName());
        patientDto.put("lastName", patient.getLastName());
        patientDto.put("patientCode", patient.getPatientCode());
        patientDto.put("phone", patient.getPhone());
        patientDto.put("email", patient.getEmail());
        patientDto.put("gender", patient.getGender() != null ? patient.getGender().name() : null);
        patientDto.put("dateOfBirth", patient.getDateOfBirth() != null ? patient.getDateOfBirth().toString() : null);
        patientDto.put("bloodGroup", patient.getBloodGroup() != null ? patient.getBloodGroup().name() : null);
        patientDto.put("address", patient.getAddress());
        patientDto.put("allergies", patient.getAllergies());
        patientDto.put("medicalHistory", patient.getMedicalHistory());
        patientDto.put("emergencyContactName", patient.getEmergencyContactName());
        patientDto.put("emergencyContactPhone", patient.getEmergencyContactPhone());

        Map<String, Object> result = new HashMap<>();
        result.put("patient", patientDto);
        result.put("registration", regDto);
        result.put("hospital", hospitalMap);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/patient/{patientId}/history")
    public ResponseEntity<?> getCasePaperHistory(@PathVariable Long patientId) {
        Long tenantId = TenantContext.getTenantId();
        patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        return ResponseEntity.ok(registrationRepository.findByPatientIdAndTenantIdOrderByIssuedAtDesc(patientId, tenantId)
                .stream()
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", r.getId());
                    m.put("issuedAt", r.getIssuedAt() != null ? r.getIssuedAt().toString() : null);
                    m.put("expiresAt", r.getExpiresAt() != null ? r.getExpiresAt().toString() : null);
                    m.put("active", r.isActive());
                    m.put("notes", r.getNotes());
                    return m;
                })
                .toList());
    }

    @DeleteMapping("/patient/{patientId}")
    public ResponseEntity<?> deleteCasePaper(@PathVariable Long patientId) {
        Long tenantId = TenantContext.getTenantId();
        patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        PatientRegistration registration = registrationRepository.findLatestActiveRegistration(patientId, tenantId)
                .orElseThrow(() -> new RuntimeException("No active case paper found"));

        registration.setActive(false);
        registrationRepository.save(registration);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Case paper deactivated successfully");
        response.put("registrationId", registration.getId());
        return ResponseEntity.ok(response);
    }
}
