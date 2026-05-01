package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bill_insurance_splits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillInsuranceSplit extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_id", nullable = false, unique = true)
    private Billing billing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurance_policy_id")
    private InsurancePolicy insurancePolicy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pre_auth_id")
    private PreAuthRequest preAuthRequest;

    @Column(name = "gross_amount", precision = 12, scale = 2)
    private BigDecimal grossAmount;

    /** Items marked isInsurancePayable=false */
    @Column(name = "non_payable_amount", precision = 12, scale = 2)
    private BigDecimal nonPayableAmount;

    /** Deductible due to room rent exceeding policy limit */
    @Column(name = "room_rent_deductible", precision = 12, scale = 2)
    private BigDecimal roomRentDeductible;

    /** Base amount that insurance covers after deductibles */
    @Column(name = "covered_base", precision = 12, scale = 2)
    private BigDecimal coveredBase;

    /** Patient share from co-pay percentage */
    @Column(name = "copay_amount", precision = 12, scale = 2)
    private BigDecimal copayAmount;

    /** Final amount patient must pay */
    @Column(name = "patient_liability", precision = 12, scale = 2)
    private BigDecimal patientLiability;

    /** Amount insurance will reimburse (claim amount) */
    @Column(name = "insurance_claim", precision = 12, scale = 2)
    private BigDecimal insuranceClaim;

    @Column(name = "calculated_at")
    @CreationTimestamp
    private LocalDateTime calculatedAt;
}
