package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Tracks the per-tenant yearly IPD admission sequence.
 * One row per (year, tenant_id) — generates admission numbers in the format IPD-YYYY-NNNNN.
 * Uses SELECT ... FOR UPDATE in the repository to guarantee uniqueness under concurrent load.
 */
@Entity
@Table(name = "ipd_admission_sequence",
       uniqueConstraints = @UniqueConstraint(columnNames = {"year", "tenant_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IpdAdmissionSequence extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "last_sequence", nullable = false)
    @Builder.Default
    private Integer lastSequence = 0;
}
