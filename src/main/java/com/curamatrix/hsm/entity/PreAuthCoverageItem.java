package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.BillingItemType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Stores the TPA-approved amount for each billing category within a pre-auth request.
 *
 * Example breakdown for a cardiac surgery pre-auth:
 *   BED_CHARGE      → approvedAmount=90000, dailyLimit=3000  (₹3000/day for 30 days)
 *   SURGERY         → approvedAmount=150000, dailyLimit=null
 *   ANAESTHESIA     → approvedAmount=25000, dailyLimit=null
 *   NURSING_CHARGE  → approvedAmount=15000, dailyLimit=500   (₹500/day)
 *   MEDICINE        → approvedAmount=20000, dailyLimit=null
 *   ICU_CHARGE      → approvedAmount=60000, dailyLimit=6000  (₹6000/day for 10 days)
 *
 * This allows the billing module to:
 *   1. Show the patient exactly what insurance covers per category
 *   2. Enforce room-rent sub-limits (if bed costs ₹5000/day but limit is ₹3000, patient pays ₹2000/day)
 *   3. Alert when a category's approved amount is nearly exhausted (trigger enhancement pre-auth)
 */
@Entity
@Table(name = "pre_auth_coverage_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreAuthCoverageItem extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pre_auth_id", nullable = false)
    private PreAuthRequest preAuth;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", length = 50, nullable = false)
    private BillingItemType itemType;

    /** Total approved amount for this category across the entire stay */
    @Column(name = "approved_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal approvedAmount;

    /** Per-day limit for recurring charges (BED_CHARGE, NURSING_CHARGE, ICU_CHARGE, DIET_CHARGE). Null for one-time items. */
    @Column(name = "daily_limit", precision = 12, scale = 2)
    private BigDecimal dailyLimit;

    @Column(name = "remarks")
    private String remarks;
}
