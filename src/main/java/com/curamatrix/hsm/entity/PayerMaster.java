package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "payer_master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayerMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "insurer_name", nullable = false)
    private String insurerName;

    @Column(name = "tpa_id")
    private String tpaId;

    @Column(name = "tpa_name")
    private String tpaName;

    @Column(name = "api_endpoint")
    private String apiEndpoint;

    @Column(name = "contact_email")
    private String contactEmail;
}
