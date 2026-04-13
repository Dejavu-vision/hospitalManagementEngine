package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_pricing_tiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePricingTier extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_service_id", nullable = false)
    private LabService labService;

    @Column(name = "tier_name", nullable = false, length = 100)
    private String tierName;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}
