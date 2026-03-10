package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByTenantKey(String tenantKey);
    boolean existsByTenantKey(String tenantKey);
    boolean existsByContactEmail(String contactEmail);
}
