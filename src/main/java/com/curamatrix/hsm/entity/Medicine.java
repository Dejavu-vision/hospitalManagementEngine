package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "medicines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Medicine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "generic_name")
    private String genericName;

    private String brand;

    private String strength;

    private String form;

    private String category;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}