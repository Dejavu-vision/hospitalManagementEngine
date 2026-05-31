package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.HospitalService;
import com.curamatrix.hsm.enums.BillingItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HospitalServiceRepository extends JpaRepository<HospitalService, Long> {
    List<HospitalService> findAllByTenantIdAndActiveTrue(Long tenantId);
    Optional<HospitalService> findByServiceCodeAndTenantId(String serviceCode, Long tenantId);
    Optional<HospitalService> findByServiceCodeAndTenantIdAndActiveTrue(String serviceCode, Long tenantId);
    Optional<HospitalService> findByIdAndTenantId(Long id, Long tenantId);
    List<HospitalService> findAllByTenantIdAndDepartmentId(Long tenantId, Long departmentId);
    List<HospitalService> findAllByItemTypeAndTenantIdAndActiveTrue(BillingItemType itemType, Long tenantId);
    List<HospitalService> findAllByTenantId(Long tenantId);
}
