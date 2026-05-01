package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.PatientFinancialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientFinancialAccountRepository extends JpaRepository<PatientFinancialAccount, Long> {
    Optional<PatientFinancialAccount> findByPatientIdAndTenantId(Long patientId, Long tenantId);
}
