package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByTenantKey(String tenantKey);
    boolean existsByTenantKey(String tenantKey);
    boolean existsByContactEmail(String contactEmail);

    /**
     * Filtered tenant listings — separate methods to avoid Hibernate 6
     * issues with IS NULL on typed parameters.
     */
    Page<Tenant> findByIsActiveAndSubscriptionPlan(Boolean isActive, String subscriptionPlan, Pageable pageable);

    Page<Tenant> findByIsActive(Boolean isActive, Pageable pageable);

    Page<Tenant> findBySubscriptionPlan(String subscriptionPlan, Pageable pageable);

    long countByIsActive(boolean isActive);
}
