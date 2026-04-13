package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.LabService;
import com.curamatrix.hsm.enums.ServiceCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LabServiceRepository extends JpaRepository<LabService, Long> {

    Optional<LabService> findByIdAndTenantId(Long id, Long tenantId);

    Optional<LabService> findByServiceCodeAndTenantId(String serviceCode, Long tenantId);

    Page<LabService> findByTenantId(Long tenantId, Pageable pageable);

    Page<LabService> findByTenantIdAndCategory(Long tenantId, ServiceCategory category, Pageable pageable);

    Page<LabService> findByTenantIdAndActive(Long tenantId, boolean active, Pageable pageable);

    Page<LabService> findByTenantIdAndCategoryAndActive(Long tenantId, ServiceCategory category, boolean active, Pageable pageable);

    @Query("SELECT ls FROM LabService ls WHERE ls.tenantId = :tenantId AND ls.active = true " +
           "AND (LOWER(ls.serviceName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(ls.serviceCode) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY ls.category, ls.serviceName")
    List<LabService> searchActiveByNameOrCode(@Param("tenantId") Long tenantId,
                                              @Param("query") String query,
                                              Pageable pageable);
}
