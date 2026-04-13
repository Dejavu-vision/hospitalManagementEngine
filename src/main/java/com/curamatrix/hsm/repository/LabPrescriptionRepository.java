package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.LabPrescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LabPrescriptionRepository extends JpaRepository<LabPrescription, Long> {

    Optional<LabPrescription> findByIdAndTenantId(Long id, Long tenantId);

    List<LabPrescription> findByPatientIdAndTenantIdOrderByCreatedAtDesc(Long patientId, Long tenantId);
}
