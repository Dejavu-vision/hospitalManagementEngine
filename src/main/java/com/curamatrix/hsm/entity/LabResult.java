package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "lab_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabResult extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_test_id", nullable = false)
    private LabTest labTest;

    @Column(name = "parameter_name", length = 200)
    private String parameterName;

    @Column(name = "result_value", nullable = false, columnDefinition = "TEXT")
    private String resultValue;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "normal_range_low", length = 50)
    private String normalRangeLow;

    @Column(name = "normal_range_high", length = 50)
    private String normalRangeHigh;

    @Column(name = "observations", columnDefinition = "TEXT")
    private String observations;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entered_by", nullable = false)
    private User enteredBy;

    @Column(name = "entered_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime enteredAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
