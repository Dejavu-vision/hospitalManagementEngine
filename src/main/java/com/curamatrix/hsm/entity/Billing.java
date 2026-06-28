package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.PaymentMethod;
import com.curamatrix.hsm.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "billings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Billing extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment; // For OPD visits

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ipd_admission_id")
    private IpdAdmission ipdAdmission; // For IPD Running Bills

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "invoice_number", unique = true, nullable = false)
    private String invoiceNumber;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(name = "insurance_adjustment", nullable = false)
    @Builder.Default
    private BigDecimal insuranceAdjustment = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false)
    private BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "paid_amount", nullable = false)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @OneToMany(mappedBy = "billing", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BillingItem> items = new ArrayList<>();

    @Column(name = "section_discounts", length = 1000)
    private String sectionDiscounts;

    @Column(name = "discount_approved", nullable = false)
    @Builder.Default
    private Boolean discountApproved = true;

    @Column(name = "discount_approved_by", length = 100)
    private String discountApprovedBy;

    @Column(name = "discount_feedback", length = 500)
    private String discountFeedback;

    public java.util.Map<String, BigDecimal> getSectionDiscountsMap() {
        if (this.sectionDiscounts == null || this.sectionDiscounts.trim().isEmpty()) {
            return new java.util.HashMap<>();
        }
        try {
            java.util.Map<String, BigDecimal> map = new java.util.HashMap<>();
            String[] pairs = this.sectionDiscounts.split(";");
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    map.put(kv[0].toUpperCase(), new BigDecimal(kv[1]));
                }
            }
            return map;
        } catch (Exception e) {
            return new java.util.HashMap<>();
        }
    }

    public void setSectionDiscountsMap(java.util.Map<String, BigDecimal> map) {
        if (map == null || map.isEmpty()) {
            this.sectionDiscounts = null;
            return;
        }
        StringBuilder sb = new StringBuilder();
        map.forEach((k, v) -> {
            if (v != null) {
                sb.append(k.toUpperCase()).append(":").append(v).append(";");
            }
        });
        this.sectionDiscounts = sb.toString();
    }
}
