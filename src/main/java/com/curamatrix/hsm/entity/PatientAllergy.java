package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.AllergySeverity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "patient_allergies", indexes = {
        @Index(name = "idx_patient_allergies_patient", columnList = "patient_id, tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientAllergy extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "allergen", nullable = false, length = 150)
    private String allergen;

    @Column(name = "reaction", length = 255)
    private String reaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 50)
    private AllergySeverity severity;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(name = "recorded_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime recordedAt;
}
