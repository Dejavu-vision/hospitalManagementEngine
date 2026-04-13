package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.TestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lab_tests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabTest extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_prescription_id", nullable = false)
    private LabPrescription labPrescription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_service_id", nullable = false)
    private LabService labService;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private TestStatus status = TestStatus.PENDING;

    @Column(name = "test_date", nullable = false)
    private LocalDate testDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "billed_price", nullable = false)
    private BigDecimal billedPrice;

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "labTest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LabResult> results = new ArrayList<>();

    @OneToMany(mappedBy = "labTest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LabDocument> documents = new ArrayList<>();
}
