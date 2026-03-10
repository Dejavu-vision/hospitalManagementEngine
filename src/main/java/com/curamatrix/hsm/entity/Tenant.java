package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_key", unique = true, nullable = false, length = 100)
    private String tenantKey; // e.g., "apollo-mumbai", "fortis-delhi"

    @Column(name = "hospital_name", nullable = false)
    private String hospitalName;

    @Column(name = "subscription_plan", nullable = false, length = 50)
    private String subscriptionPlan; // BASIC, STANDARD, PREMIUM

    @Column(name = "subscription_start", nullable = false)
    private LocalDate subscriptionStart;

    @Column(name = "subscription_end", nullable = false)
    private LocalDate subscriptionEnd;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "max_users")
    @Builder.Default
    private Integer maxUsers = 50;

    @Column(name = "max_patients")
    @Builder.Default
    private Integer maxPatients = 10000;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 500)
    private String logo; // URL to hospital logo

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private Map<String, Object> settings; // Custom configuration per tenant

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
