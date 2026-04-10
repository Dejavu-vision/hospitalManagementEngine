package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.BillingItemType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "hospital_services", uniqueConstraints = {
    @UniqueConstraint(name = "uk_service_code_tenant", columnNames = {"service_code", "tenant_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class HospitalService extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "service_code", nullable = false)
    private String serviceCode;

    @Column(nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 50)
    private BillingItemType itemType;

    @Builder.Default
    @Column(name = "is_active")
    private boolean active = true;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "validity_period_days")
    private Integer validityPeriodDays;
}
