package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Maintains a per-tenant, per-role-prefix counter for generating
 * employee IDs like ADM001, DOC042, REC007.
 *
 * <p>Concurrency is handled via pessimistic row-level locking
 * (SELECT … FOR UPDATE) so that two concurrent inserts for the
 * same tenant + prefix never produce the same number.</p>
 *
 * <p>The {@code @Version} column provides an additional optimistic
 * locking safety net for any non-locked update paths.</p>
 */
@Entity
@Table(
        name = "employee_id_sequences",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_emp_seq_tenant_prefix",
                columnNames = {"tenant_id", "prefix"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeIdSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The tenant (hospital) this sequence belongs to. */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** Role prefix: ADM, DOC, REC, etc. */
    @Column(name = "prefix", nullable = false, length = 10)
    private String prefix;

    /** The next number to be allocated. Starts at 1. */
    @Column(name = "next_number", nullable = false)
    private Long nextNumber;

    /** Optimistic lock version — safety net on top of pessimistic lock. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
