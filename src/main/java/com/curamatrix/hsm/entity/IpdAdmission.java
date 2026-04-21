package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.AdmissionStatus;
import com.curamatrix.hsm.enums.AdmissionType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ipd_admissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IpdAdmission extends TenantAwareEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "admission_number", nullable = false)
    private String admissionNumber; 
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_doctor_id", nullable = false)
    private Doctor primaryDoctor;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opd_appointment_id")
    private Appointment opdAppointment;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "admission_type", length = 50, nullable = false)
    private AdmissionType admissionType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private AdmissionStatus status;
    
    @Column(name = "admission_time", nullable = false)
    private LocalDateTime admissionTime;
    
    @Column(name = "expected_discharge_time")
    private LocalDateTime expectedDischargeTime;
    
    @Column(name = "actual_discharge_time")
    private LocalDateTime actualDischargeTime;
    
    @Column(name = "discharge_summary", columnDefinition = "TEXT")
    private String dischargeSummary;
    
    @Column(name = "admission_notes", columnDefinition = "TEXT")
    private String admissionNotes;
}
