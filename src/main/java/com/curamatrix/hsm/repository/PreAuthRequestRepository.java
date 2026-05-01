package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.PreAuthRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PreAuthRequestRepository extends JpaRepository<PreAuthRequest, Long> {
    Page<PreAuthRequest> findByTenantId(Long tenantId, Pageable pageable);
    Page<PreAuthRequest> findByPatientIdAndTenantId(Long patientId, Long tenantId, Pageable pageable);
}
