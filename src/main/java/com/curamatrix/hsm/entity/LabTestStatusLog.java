package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.TestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "lab_test_status_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabTestStatusLog extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_test_id", nullable = false)
    private LabTest labTest;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 50)
    private TestStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 50)
    private TestStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime changedAt;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
}
