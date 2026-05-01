package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.PaymentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentPlanRepository extends JpaRepository<PaymentPlan, Long> {
    List<PaymentPlan> findAllByPatientIdAndTenantId(Long patientId, Long tenantId);
    Optional<PaymentPlan> findFirstByPatientIdAndTenantIdAndStatusOrderByCreatedAtDesc(Long patientId, Long tenantId, com.curamatrix.hsm.enums.PaymentPlanStatus status);
}
