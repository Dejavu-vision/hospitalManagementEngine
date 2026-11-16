package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Audit trail of who opened a patient's record. */
@Entity
@Table(name = "patient_access_logs", indexes = {
        @Index(name = "idx_patient_access_patient", columnList = "patient_id, tenant_id, accessed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientAccessLog extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "user_email", length = 150)
    private String userEmail;

    @Column(name = "user_role", length = 50)
    private String userRole;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "accessed_at", nullable = false)
    private LocalDateTime accessedAt;
}
