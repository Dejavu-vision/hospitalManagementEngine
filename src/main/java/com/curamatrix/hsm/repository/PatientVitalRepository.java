package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.PatientVital;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientVitalRepository extends JpaRepository<PatientVital, Long> {
    List<PatientVital> findByPatientIdAndTenantIdOrderByRecordedAtDesc(Long patientId, Long tenantId, Pageable pageable);

    List<PatientVital> findByPatientIdAndTenantIdAndAppointmentIdOrderByRecordedAtDesc(Long patientId, Long tenantId, Long appointmentId);
}
