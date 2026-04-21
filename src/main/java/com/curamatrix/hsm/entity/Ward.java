package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "wards", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name", "tenant_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ward extends TenantAwareEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "floor")
    private String floor;
    
    @Column(name = "description", length = 500)
    private String description;
}
