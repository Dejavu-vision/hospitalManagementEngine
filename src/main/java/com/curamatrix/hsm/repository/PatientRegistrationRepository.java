package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.PatientRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientRegistrationRepository extends JpaRepository<PatientRegistration, Long> {
    
    @Query("SELECT pr FROM PatientRegistration pr WHERE pr.patient.id = :patientId AND pr.tenantId = :tenantId AND pr.active = true ORDER BY pr.expiresAt DESC LIMIT 1")
    Optional<PatientRegistration> findLatestActiveRegistration(@Param("patientId") Long patientId, @Param("tenantId") Long tenantId);

    List<PatientRegistration> findByPatientIdAndTenantIdOrderByIssuedAtDesc(Long patientId, Long tenantId);
}
