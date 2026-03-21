package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "role_pages", uniqueConstraints = {
        @UniqueConstraint(name = "uk_role_page", columnNames = {"role_id", "page_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "page_id", nullable = false)
    private UiPage page;
}
