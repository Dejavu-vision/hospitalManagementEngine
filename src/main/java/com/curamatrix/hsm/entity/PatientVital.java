package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Structured OPD vitals recorded by reception/nursing/doctor. */
@Entity
@Table(name = "patient_vitals", indexes = {
        @Index(name = "idx_patient_vitals_patient", columnList = "patient_id, tenant_id, recorded_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientVital extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    /** Optional link to the visit during which the vitals were taken. */
    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "temperature_c", precision = 4, scale = 1)
    private BigDecimal temperatureC;

    @Column(name = "bp_systolic")
    private Integer bpSystolic;

    @Column(name = "bp_diastolic")
    private Integer bpDiastolic;

    @Column(name = "pulse")
    private Integer pulse;

    @Column(name = "spo2")
    private Integer spo2;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "weight_kg", precision = 5, scale = 1)
    private BigDecimal weightKg;

    @Column(name = "height_cm", precision = 5, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "blood_sugar", precision = 5, scale = 1)
    private BigDecimal bloodSugar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    void defaultRecordedAt() {
        if (recordedAt == null) recordedAt = LocalDateTime.now();
    }
}
