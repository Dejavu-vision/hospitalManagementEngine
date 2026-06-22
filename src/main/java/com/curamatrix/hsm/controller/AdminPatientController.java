package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.entity.Patient;
import com.curamatrix.hsm.entity.Tenant;
import com.curamatrix.hsm.repository.PatientRepository;
import com.curamatrix.hsm.repository.TenantRepository;
import com.curamatrix.hsm.service.PatientPurgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/patients")
@RequiredArgsConstructor
public class AdminPatientController {

    private final PatientPurgeService patientPurgeService;
    private final TenantRepository tenantRepository;
    private final PatientRepository patientRepository;

    /**
     * Lists all hospitals (tenants) for super admin.
     */
    @GetMapping("/hospitals")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<Tenant>> listHospitals() {
        return ResponseEntity.ok(tenantRepository.findAll());
    }

    /**
     * Searches patients within a specific hospital (tenant).
     * Returns a simplified DTO to avoid Hibernate proxy serialization issues.
     */
    @GetMapping("/hospitals/{tenantId}/patients")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> searchPatientsInHospital(
            @PathVariable Long tenantId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Hospital not found"));
        String q = search.trim().isEmpty() ? null : search.trim();
        Page<Patient> patients = patientRepository.searchWithFilters(
                q, null, null, tenantId, PageRequest.of(page, size));

        // Map to simple DTOs to avoid lazy proxy serialization errors
        Page<Map<String, Object>> dtoPage = patients.map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("patientCode", p.getPatientCode());
            m.put("firstName", p.getFirstName());
            m.put("lastName", p.getLastName());
            m.put("phone", p.getPhone());
            m.put("gender", p.getGender() != null ? p.getGender().name() : null);
            m.put("registeredAt", p.getRegisteredAt() != null ? p.getRegisteredAt().toString() : null);
            return m;
        });
        return ResponseEntity.ok(dtoPage);
    }

    /**
     * Permanently deletes a patient and ALL related data.
     * Super-admin only. This cannot be undone.
     * Accepts tenantId as query param to purge patient from a specific hospital.
     */
    @DeleteMapping("/{patientId}/purge")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> purgePatient(
            @PathVariable Long patientId,
            @RequestParam(required = false) Long tenantId) {
        log.warn("Admin purge request for patient {} in tenant {}", patientId, tenantId);
        if (tenantId != null) {
            patientPurgeService.purgePatientForTenant(patientId, tenantId);
        } else {
            patientPurgeService.purgePatient(patientId);
        }
        Map<String, String> response = new HashMap<>();
        response.put("message", "Patient and all related data permanently deleted");
        return ResponseEntity.ok(response);
    }
}
