package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Tracks the daily walk-in token sequence per doctor per tenant.
 * One sequence per (appointment_date, tenant_id, doctor_id) — each doctor
 * gets an independent counter starting at T-001 for the day.
 */
@Entity
@Table(name = "walk_in_token_sequence",
       uniqueConstraints = @UniqueConstraint(columnNames = {"appointment_date", "tenant_id", "doctor_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalkInTokenSequence extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    /** The doctor whose token sequence this row tracks. */
    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    // Database has both 'counter' and 'last_token' columns - keep them in sync
    @Column(name = "last_token", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    @Builder.Default
    private Integer lastToken = 0;

    @Column(name = "counter", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    @Builder.Default
    private Integer counter = 0;
}
