package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.PolicyType;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "insurance_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsurancePolicy extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @JsonBackReference
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_id")
    private PayerMaster payer;

    @Column(name = "policy_number", nullable = false)
    private String policyNumber;

    @Column(name = "member_id")
    private String memberId;

    @Column(name = "sum_insured", precision = 12, scale = 2)
    private BigDecimal sumInsured;

    @Column(name = "room_rent_limit", precision = 12, scale = 2)
    private BigDecimal roomRentLimit;

    @Column(name = "copay_pct", precision = 5, scale = 2)
    private BigDecimal copayPct;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", length = 50)
    private PolicyType policyType;
}
