package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.BedType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rooms", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"room_number", "ward_id", "tenant_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room extends TenantAwareEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "room_number", nullable = false)
    private String roomNumber;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_id", nullable = false)
    private Ward ward;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", length = 50)
    private BedType roomType; 
    
    @Column(name = "amenities")
    private String amenities;
}
