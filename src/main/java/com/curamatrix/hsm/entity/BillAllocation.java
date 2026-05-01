package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "bill_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillAllocation extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Billing billing;

    @Column(name = "allocated_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal allocatedAmount;
}
