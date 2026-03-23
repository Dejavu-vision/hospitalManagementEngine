package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.enums.Shift;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "receptionists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Receptionist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    /**
     * Employee ID is now the single source of truth on the User entity.
     * This column is kept for backward compatibility with existing data
     * but is always synced from user.employeeId on save.
     */
    @Column(name = "employee_id")
    private String employeeId;

    @Enumerated(EnumType.STRING)
    private Shift shift;
}
