package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Tracks the daily walk-in token sequence per tenant.
 * One sequence per (appointment_date, tenant_id) — tokens are unique across
 * ALL doctors and departments for the day, so patients get a single queue number
 * regardless of which doctor they are seeing.
 */
@Entity
@Table(name = "walk_in_token_sequence",
       uniqueConstraints = @UniqueConstraint(columnNames = {"appointment_date", "tenant_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalkInTokenSequence extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "last_token", nullable = false)
    @Builder.Default
    private Integer lastToken = 0;
}
