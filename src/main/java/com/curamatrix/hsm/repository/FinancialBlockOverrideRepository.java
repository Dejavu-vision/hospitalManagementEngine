package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.FinancialBlockOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FinancialBlockOverrideRepository extends JpaRepository<FinancialBlockOverride, Long> {
    List<FinancialBlockOverride> findAllByPatientIdAndTenantId(Long patientId, Long tenantId);
}
