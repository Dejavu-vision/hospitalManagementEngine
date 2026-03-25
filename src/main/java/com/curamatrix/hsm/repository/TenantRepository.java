package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByTenantKey(String tenantKey);
    boolean existsByTenantKey(String tenantKey);
    boolean existsByContactEmail(String contactEmail);

    /**
     * Filtered + paginated tenant listing for Super Admin dashboard.
     */
    @Query("SELECT t FROM Tenant t WHERE " +
           "(:isActive IS NULL OR t.isActive = :isActive) AND " +
           "(:plan IS NULL OR t.subscriptionPlan = :plan)")
    Page<Tenant> findByFilters(@Param("isActive") Boolean isActive,
                               @Param("plan") String plan,
                               Pageable pageable);

    long countByIsActive(boolean isActive);
}
