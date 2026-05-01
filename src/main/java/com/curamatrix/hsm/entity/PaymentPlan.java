package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.PaymentPlanStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentPlan extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    // A JSON string storing the array of {dueDate, amount, paid} 
    // Example: [{"dueDate":"2026-06-01", "amount": 15000.00, "paid": false}]
    @Column(columnDefinition = "JSON")
    private String installments;

    @Column(name = "guarantor_name")
    private String guarantorName;

    @Column(name = "guarantor_contact")
    private String guarantorContact;

    @Column(name = "approved_by_id")
    private Long approvedById;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    @Builder.Default
    private PaymentPlanStatus status = PaymentPlanStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
