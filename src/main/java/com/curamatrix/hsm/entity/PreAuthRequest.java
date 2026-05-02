package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.PreAuthStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pre_auth_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreAuthRequest extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurance_policy_id", nullable = false)
    private InsurancePolicy insurancePolicy;

    @Column(name = "admission_id")
    private Long admissionId;

    @Column(name = "appointment_id")
    private Long appointmentId;

    // ── Claim type ────────────────────────────────────────────────────────────
    /** CASHLESS or REIMBURSEMENT */
    @Column(name = "claim_type", length = 50)
    @Builder.Default
    private String claimType = "CASHLESS";

    // ── Clinical coding ───────────────────────────────────────────────────────
    /** ICD-10 diagnosis code (e.g. I21.0 for Acute MI) */
    @Column(name = "diagnosis_code", length = 100)
    private String diagnosisCode;

    /** ICD procedure code (e.g. 36.01 for CABG) */
    @Column(name = "procedure_code", length = 100)
    private String procedureCode;

    // ── Amounts ───────────────────────────────────────────────────────────────
    @Column(name = "estimated_amount", precision = 12, scale = 2)
    private BigDecimal estimatedAmount;

    /** Total approved amount (sum of all coverage items) */
    @Column(name = "approved_amount", precision = 12, scale = 2)
    private BigDecimal approvedAmount;

    /** Final claim amount submitted to TPA at discharge */
    @Column(name = "final_claim_amount", precision = 12, scale = 2)
    private BigDecimal finalClaimAmount;

    /** Amount actually settled/paid by TPA */
    @Column(name = "final_settled_amount", precision = 12, scale = 2)
    private BigDecimal finalSettledAmount;

    // ── Status ────────────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private PreAuthStatus status;

    @Column(name = "tpa_reference_number")
    private String tpaReferenceNumber;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    /** Hospital's response to a TPA query (when status = QUERY_RAISED) */
    @Column(name = "query_response", columnDefinition = "TEXT")
    private String queryResponse;

    // ── Enhancement tracking ──────────────────────────────────────────────────
    /** True if this is an enhancement request (additional funds after initial approval exhausted) */
    @Column(name = "is_enhancement", nullable = false)
    @Builder.Default
    private Boolean isEnhancement = false;

    /** For enhancement requests — links back to the original pre-auth */
    @Column(name = "parent_pre_auth_id")
    private Long parentPreAuthId;

    // ── Timestamps ────────────────────────────────────────────────────────────
    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
