package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.ServiceCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lab_services", uniqueConstraints = {
    @UniqueConstraint(name = "uk_lab_service_code_tenant", columnNames = {"service_code", "tenant_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabService extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "service_code", nullable = false, length = 50)
    private String serviceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private ServiceCategory category;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "default_price", nullable = false)
    private BigDecimal defaultPrice;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "labService", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ServicePricingTier> pricingTiers = new ArrayList<>();
}
