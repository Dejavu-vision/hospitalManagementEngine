package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a token number that has been reserved/blocked for a specific day.
 * Blocked tokens are held out of the auto-increment sequence and can be
 * manually assigned to VIP or emergency patients.
 *
 * Lifecycle: BLOCKED → ASSIGNED (when given to a patient)
 *                    → RELEASED (when manually released or auto-released at end of day)
 */
@Entity
@Table(name = "blocked_tokens",
       uniqueConstraints = @UniqueConstraint(columnNames = {"token_number", "appointment_date", "tenant_id", "doctor_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BlockedToken extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_number", nullable = false)
    private Integer tokenNumber;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    /**
     * The doctor whose queue this block applies to.
     * Nullable for backward compatibility with legacy rows that predate per-doctor scoping.
     */
    @Column(name = "doctor_id")
    private Long doctorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private BlockedTokenStatus status = BlockedTokenStatus.BLOCKED;

    /** Why this token was blocked — e.g. "VIP Reserve", "Emergency slot" */
    @Column(name = "reason", length = 255)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_by")
    private User blockedBy;

    @Column(name = "blocked_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime blockedAt;

    /** Set when this blocked token is assigned to a walk-in appointment */
    @Column(name = "assigned_to_appointment_id")
    private Long assignedToAppointmentId;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    public enum BlockedTokenStatus {
        BLOCKED,   // Reserved, not yet used
        ASSIGNED,  // Given to a patient
        RELEASED   // Released back to pool (unused)
    }
}
