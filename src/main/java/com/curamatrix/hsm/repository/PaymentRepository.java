package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findAllByPatientIdAndTenantId(Long patientId, Long tenantId);
}
