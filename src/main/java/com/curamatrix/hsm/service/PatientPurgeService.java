package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.PatientRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientPurgeService {

    private final PatientRepository patientRepository;
    private final EntityManager em;

    @Transactional
    public void purgePatient(Long patientId) {
        Long tenantId = TenantContext.getTenantId();
        purgePatientForTenant(patientId, tenantId);
    }

    @Transactional
    public void purgePatientForTenant(Long patientId, Long tenantId) {
        log.warn("PURGE: Deleting patient {} and all related data for tenant {}", patientId, tenantId);

        patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId));

        // Disable FK checks for clean cascade deletion
        em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();

        try {
            exec("DELETE FROM appointment_status_log WHERE appointment_id IN (SELECT id FROM appointments WHERE patient_id = ?1 AND tenant_id = ?2)", patientId, tenantId);
            exec("DELETE FROM prescriptions WHERE diagnosis_id IN (SELECT id FROM diagnoses WHERE appointment_id IN (SELECT id FROM appointments WHERE patient_id = ?1 AND tenant_id = ?2))", patientId, tenantId);
            exec("DELETE FROM diagnoses WHERE appointment_id IN (SELECT id FROM appointments WHERE patient_id = ?1 AND tenant_id = ?2)", patientId, tenantId);
            exec("DELETE FROM lab_prescriptions WHERE patient_id = ?1 AND tenant_id = ?2", patientId, tenantId);
            exec("DELETE FROM bill_insurance_splits WHERE billing_id IN (SELECT id FROM billings WHERE patient_id = ?1 AND tenant_id = ?2)", patientId, tenantId);
            exec("DELETE FROM bill_allocations WHERE bill_id IN (SELECT id FROM billings WHERE patient_id = ?1 AND tenant_id = ?2)", patientId, tenantId);
            exec("DELETE FROM billing_items WHERE billing_id IN (SELECT id FROM billings WHERE patient_id = ?1 AND tenant_id = ?2)", patientId, tenantId);
            exec("DELETE FROM patient_registrations WHERE patient_id = ?1 AND tenant_id = ?2", patientId, tenantId);
            exec("DELETE FROM payments WHERE patient_id = ?1 AND tenant_id = ?2", patientId, tenantId);
            exec("DELETE FROM ipd_vital_signs WHERE ipd_admission_id IN (SELECT id FROM ipd_admissions WHERE patient_id = ?1 AND tenant_id = ?2)", patientId, tenantId);
            exec("DELETE FROM ipd_daily_progress_notes WHERE ipd_admission_id IN (SELECT id FROM ipd_admissions WHERE patient_id = ?1 AND tenant_id = ?2)", patientId, tenantId);
            exec("DELETE FROM bed_allocations WHERE admission_id IN (SELECT id FROM ipd_admissions WHERE patient_id = ?1 AND tenant_id = ?2)", patientId, tenantId);
            exec("DELETE FROM pre_auth_requests WHERE patient_id = ?1 AND tenant_id = ?2", patientId, tenantId);
            exec("DELETE FROM billings WHERE patient_id = ?1 AND tenant_id = ?2", patientId, tenantId);
            exec("DELETE FROM ipd_admissions WHERE patient_id = ?1 AND tenant_id = ?2", patientId, tenantId);
            exec("DELETE FROM appointments WHERE patient_id = ?1 AND tenant_id = ?2", patientId, tenantId);
            exec("DELETE FROM financial_blocks WHERE patient_id = ?1 AND tenant_id = ?2", patientId, tenantId);
            exec("DELETE FROM payment_plans WHERE patient_id = ?1 AND tenant_id = ?2", patientId, tenantId);
            exec("DELETE FROM patient_financial_account WHERE patient_id = ?1 AND tenant_id = ?2", patientId, tenantId);
            exec("DELETE FROM insurance_policies WHERE patient_id = ?1 AND tenant_id = ?2", patientId, tenantId);
            exec("DELETE FROM patients WHERE id = ?1 AND tenant_id = ?2", patientId, tenantId);
        } finally {
            em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
        }

        log.warn("PURGE COMPLETE: Patient {} deleted from tenant {}", patientId, tenantId);
    }

    private void exec(String sql, Long patientId, Long tenantId) {
        em.createNativeQuery(sql)
                .setParameter(1, patientId)
                .setParameter(2, tenantId)
                .executeUpdate();
    }
}
