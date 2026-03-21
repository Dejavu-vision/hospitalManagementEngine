package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ui_pages", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ui_page_key", columnNames = {"page_key"}),
        @UniqueConstraint(name = "uk_ui_page_route", columnNames = {"route"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UiPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_key", nullable = false, length = 100)
    private String pageKey;

    @Column(name = "route", nullable = false, length = 200)
    private String route;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
