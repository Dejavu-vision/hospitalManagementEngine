package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Diagnosis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {

    Optional<Diagnosis> findByAppointmentId(Long appointmentId);

    boolean existsByAppointmentId(Long appointmentId);

    List<Diagnosis> findByAppointmentPatientIdOrderByCreatedAtDesc(Long patientId);

    // Tenant-scoped patient history ordered by most recent first
    @Query("SELECT d FROM Diagnosis d WHERE d.appointment.patient.id = :patientId " +
           "AND d.tenantId = :tenantId ORDER BY d.createdAt DESC")
    List<Diagnosis> findByPatientIdAndTenantId(@Param("patientId") Long patientId,
                                                @Param("tenantId") Long tenantId);
}
