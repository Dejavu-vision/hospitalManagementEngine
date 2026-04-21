package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.IpdAdmission;
import com.curamatrix.hsm.enums.AdmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IpdAdmissionRepository extends JpaRepository<IpdAdmission, Long> {
    
    @Query("SELECT a FROM IpdAdmission a WHERE a.tenantId = :tenantId AND " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:patientId IS NULL OR a.patient.id = :patientId) AND " +
           "(:doctorId IS NULL OR a.primaryDoctor.id = :doctorId)")
    Page<IpdAdmission> findByFilters(@Param("tenantId") Long tenantId,
                                     @Param("status") AdmissionStatus status,
                                     @Param("patientId") Long patientId,
                                     @Param("doctorId") Long doctorId,
                                     Pageable pageable);
                                     
    List<IpdAdmission> findByPatientIdAndTenantId(Long patientId, Long tenantId);
    
    // To check if patient already has an active admission
    boolean existsByPatientIdAndStatusAndTenantId(Long patientId, AdmissionStatus status, Long tenantId);

    List<IpdAdmission> findByStatusAndTenantId(AdmissionStatus status, Long tenantId);
}
