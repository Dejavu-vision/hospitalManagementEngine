package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    // Optional: Can be null if it's a consolidated payment covering multiple bills
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id")
    private Billing billing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_item_id")
    private BillingItem billingItem;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private PaymentMethod method;

    @Column(name = "reference_number")
    private String referenceNumber;

    @Column(name = "collected_by_id")
    private Long collectedById;

    @Column(name = "receipt_number")
    private String receiptNumber;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime timestamp;
}


