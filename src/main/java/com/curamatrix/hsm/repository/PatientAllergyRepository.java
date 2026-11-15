package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.PatientAllergy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PatientAllergyRepository extends JpaRepository<PatientAllergy, Long> {
    List<PatientAllergy> findByPatientIdAndTenantIdAndActiveTrueOrderByRecordedAtDesc(Long patientId, Long tenantId);
    Optional<PatientAllergy> findByIdAndPatientIdAndTenantId(Long id, Long patientId, Long tenantId);
}
