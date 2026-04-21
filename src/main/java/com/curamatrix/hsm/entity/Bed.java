package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.BedStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "beds", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"bed_number", "room_id", "tenant_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bed extends TenantAwareEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "bed_number", nullable = false)
    private String bedNumber;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private BedStatus status;
    
    @Column(name = "daily_price", precision = 10, scale = 2)
    private BigDecimal dailyPrice;
}
