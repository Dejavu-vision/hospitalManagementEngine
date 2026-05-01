package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.BlockType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "financial_blocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialBlockOverride extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", length = 50, nullable = false)
    private BlockType blockType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by_id")
    private Long createdById;

    @Column(nullable = false)
    @Builder.Default
    private boolean overridden = false;

    @Column(name = "override_reason")
    private String overrideReason;

    @Column(name = "override_by_id")
    private Long overrideById;
}
