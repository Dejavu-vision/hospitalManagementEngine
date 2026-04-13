package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Billing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingRepository extends JpaRepository<Billing, Long> {
    java.util.List<Billing> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);
    java.util.List<Billing> findAllByPatientIdAndTenantId(Long patientId, Long tenantId);
    Page<Billing> findByPatientId(Long patientId, Pageable pageable);
    java.util.Optional<Billing> findByAppointmentIdAndTenantId(Long appointmentId, Long tenantId);
}
