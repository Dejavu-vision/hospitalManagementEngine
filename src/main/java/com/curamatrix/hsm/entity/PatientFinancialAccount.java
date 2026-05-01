package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.FinancialStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "patient_financial_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientFinancialAccount extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "total_billed_lifetime", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalBilledLifetime = BigDecimal.ZERO;

    @Column(name = "total_paid_lifetime", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalPaidLifetime = BigDecimal.ZERO;

    @Column(name = "current_outstanding", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal currentOutstanding = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "financial_status", length = 50, nullable = false)
    @Builder.Default
    private FinancialStatus financialStatus = FinancialStatus.REGISTERED_UNPAID;

    @Column(name = "last_payment_date")
    private LocalDateTime lastPaymentDate;

    @Column(name = "credit_limit", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(name = "payment_plan_active")
    @Builder.Default
    private boolean paymentPlanActive = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
