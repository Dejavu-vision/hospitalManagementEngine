package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.InsurancePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InsurancePolicyRepository extends JpaRepository<InsurancePolicy, Long> {
    List<InsurancePolicy> findByPatientIdAndTenantId(Long patientId, Long tenantId);
    Optional<InsurancePolicy> findByIdAndTenantId(Long id, Long tenantId);
}
