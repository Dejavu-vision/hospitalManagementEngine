package com.curamatrix.hsm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Direct user → page mapping with an effect (GRANT or DENY).
 *
 * GRANT = give the user access to a page they wouldn't get from their role.
 * DENY  = block a page that the user's role would normally provide.
 *
 * Effective pages = (role default pages − DENY pages) + GRANT pages.
 */
@Entity
@Table(name = "user_pages", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_page", columnNames = {"user_id", "page_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPage extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "page_id", nullable = false)
    private UiPage page;

    /**
     * GRANT = extra page added beyond role defaults.
     * DENY  = role-default page explicitly removed for this user.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "effect", nullable = false, length = 5)
    @Builder.Default
    private Effect effect = Effect.GRANT;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum Effect {
        GRANT, DENY
    }
}
