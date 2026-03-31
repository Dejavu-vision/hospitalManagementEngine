package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "walk_in_token_sequence",
       uniqueConstraints = @UniqueConstraint(columnNames = {"doctor_id", "appointment_date", "tenant_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalkInTokenSequence extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "last_token", nullable = false)
    @Builder.Default
    private Integer lastToken = 0;
}
