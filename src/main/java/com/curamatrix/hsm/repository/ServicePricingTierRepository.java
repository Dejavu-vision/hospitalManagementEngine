package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.ServicePricingTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ServicePricingTierRepository extends JpaRepository<ServicePricingTier, Long> {

    List<ServicePricingTier> findByLabServiceIdAndTenantIdOrderByValidFromDesc(Long labServiceId, Long tenantId);

    Optional<ServicePricingTier> findByIdAndTenantId(Long id, Long tenantId);

    @Query("SELECT t FROM ServicePricingTier t WHERE t.labService.id = :serviceId " +
           "AND t.tenantId = :tenantId AND t.validFrom <= :date " +
           "AND (t.validTo IS NULL OR t.validTo >= :date) ORDER BY t.validFrom DESC")
    List<ServicePricingTier> findActiveTiers(@Param("serviceId") Long serviceId,
                                             @Param("tenantId") Long tenantId,
                                             @Param("date") LocalDate date);
}
