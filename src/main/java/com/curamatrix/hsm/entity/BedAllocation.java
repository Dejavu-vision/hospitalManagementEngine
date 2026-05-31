package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bed_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BedAllocation extends TenantAwareEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_id", nullable = false)
    private IpdAdmission admission;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bed_id", nullable = false)
    private Bed bed;
    
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;
    
    @Column(name = "end_time")
    private LocalDateTime endTime;
    
    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent;
    
    @Column(name = "daily_price_at_time", precision = 10, scale = 2)
    private BigDecimal dailyPriceAtTime;
    
    @Column(name = "nursing_charge_at_time", precision = 10, scale = 2)
    private BigDecimal nursingChargeAtTime;
    
    @Column(name = "diet_charge_at_time", precision = 10, scale = 2)
    private BigDecimal dietChargeAtTime;
    
    @Column(name = "transfer_reason")
    private String transferReason;
}
