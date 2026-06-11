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

    Optional<Billing> findByIpdAdmissionId(Long ipdAdmissionId);

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

    @Query("SELECT b FROM Billing b WHERE b.tenantId = :tenantId AND (b.paymentStatus IN :statuses OR b.createdAt >= :startOfDay) ORDER BY b.createdAt DESC")
    List<Billing> findActiveOpdBills(@Param("tenantId") Long tenantId, @Param("statuses") List<PaymentStatus> statuses, @Param("startOfDay") LocalDateTime startOfDay);

    // Pending OPD bills filtered by date range (for Pending tab with date filter)
    @Query("SELECT b FROM Billing b WHERE b.tenantId = :tenantId AND b.ipdAdmission IS NULL AND b.paymentStatus IN :statuses AND b.createdAt >= :startDate AND b.createdAt <= :endDate ORDER BY b.createdAt DESC")
    List<Billing> findOpdBillsByStatusAndDateRange(@Param("tenantId") Long tenantId, @Param("statuses") List<PaymentStatus> statuses, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // Paid bills (IPD or OPD) filtered by paidAt date range (for Paid tab)
    @Query("SELECT b FROM Billing b WHERE b.tenantId = :tenantId AND b.paymentStatus = 'PAID' AND b.paidAt >= :startDate AND b.paidAt <= :endDate ORDER BY b.paidAt DESC")
    List<Billing> findPaidBillsByDateRange(@Param("tenantId") Long tenantId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // Pending OPD bills without date range (for Pending tab default view)
    @Query("SELECT b FROM Billing b WHERE b.tenantId = :tenantId AND b.ipdAdmission IS NULL AND b.paymentStatus IN :statuses ORDER BY b.createdAt DESC")
    List<Billing> findPendingOpdBills(@Param("tenantId") Long tenantId, @Param("statuses") List<PaymentStatus> statuses);

    // Discharged IPD admissions with unpaid (PENDING/PARTIAL) bills, optionally filtered by actual discharge date
    @Query("SELECT b FROM Billing b WHERE b.tenantId = :tenantId AND b.ipdAdmission IS NOT NULL AND b.ipdAdmission.status = 'DISCHARGED' AND b.paymentStatus IN :statuses AND (:startDate IS NULL OR b.ipdAdmission.actualDischargeTime >= :startDate) AND (:endDate IS NULL OR b.ipdAdmission.actualDischargeTime <= :endDate) ORDER BY b.ipdAdmission.actualDischargeTime DESC")
    List<Billing> findUnpaidDischargedBills(@Param("tenantId") Long tenantId, @Param("statuses") List<PaymentStatus> statuses, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // Paid bills without date filter (returns all paid, used as fallback)
    List<Billing> findAllByTenantIdAndPaymentStatusInOrderByPaidAtDesc(Long tenantId, List<PaymentStatus> statuses);
}
