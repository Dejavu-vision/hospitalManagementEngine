package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.AdmissionStatus;
import com.curamatrix.hsm.enums.AdmissionType;
import com.curamatrix.hsm.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ipd_admissions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"admission_number", "tenant_id"}))
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

    @Column(name = "deposit_amount", precision = 10, scale = 2)
    private BigDecimal depositAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 50)
    private PaymentMethod paymentMethod;

    @Column(name = "pre_auth_id")
    private Long preAuthId;

    /** Set to true by the doctor to signal all clinical work is done and discharge is cleared. */
    @Column(name = "discharge_cleared", nullable = false)
    private boolean dischargeCleared = false;

    /** Set to true once Generate Invoice has been called and a final invoice exists. */
    @Column(name = "invoice_generated", nullable = false)
    private boolean invoiceGenerated = false;
}
