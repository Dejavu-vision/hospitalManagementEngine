package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Billing;
import com.curamatrix.hsm.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BillingRepository extends JpaRepository<Billing, Long> {

    List<Billing> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);

    List<Billing> findAllByPatientIdAndTenantId(Long patientId, Long tenantId);

    Page<Billing> findByPatientId(Long patientId, Pageable pageable);

    Optional<Billing> findByAppointmentIdAndTenantId(Long appointmentId, Long tenantId);

    Optional<Billing> findByIdAndTenantId(Long id, Long tenantId);

    List<Billing> findAllByTenantIdAndPaymentStatusOrderByCreatedAtDesc(Long tenantId, PaymentStatus status);

    long countByTenantIdAndPaymentStatus(Long tenantId, PaymentStatus status);

    long countByTenantId(Long tenantId);

    @Query("SELECT COALESCE(SUM(b.netAmount), 0) FROM Billing b WHERE b.tenantId = :tenantId AND b.paymentStatus = :status")
    BigDecimal sumNetAmountByTenantIdAndPaymentStatus(@Param("tenantId") Long tenantId, @Param("status") PaymentStatus status);

    @Query("SELECT COALESCE(SUM(b.paidAmount), 0) FROM Billing b WHERE b.tenantId = :tenantId AND b.paidAt >= :startOfDay AND b.paidAt < :endOfDay")
    BigDecimal sumPaidAmountToday(@Param("tenantId") Long tenantId, @Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);
}
