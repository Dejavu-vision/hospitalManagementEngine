package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.DoctorStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "doctor_availability",
       uniqueConstraints = @UniqueConstraint(columnNames = {"doctor_id", "availability_date", "tenant_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DoctorAvailability extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "availability_date", nullable = false)
    private LocalDate availabilityDate;

    @Column(name = "is_present", nullable = false)
    @Builder.Default
    private Boolean isPresent = true;

    /** Real-time status during the duty day */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private DoctorStatus status = DoctorStatus.ON_DUTY;

    /** Optional note — e.g. "Back in 30 min", "Emergency surgery" */
    @Column(name = "status_note")
    private String statusNote;

    /** Estimated return time — set when ON_BREAK or IN_SURGERY */
    @Column(name = "available_from")
    private LocalTime availableFrom;

    /** Duty start time */
    @Column(name = "duty_start")
    private LocalTime dutyStart;

    /** Duty end time */
    @Column(name = "duty_end")
    private LocalTime dutyEnd;
}
