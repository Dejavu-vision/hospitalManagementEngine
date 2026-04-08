package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.HospitalService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HospitalServiceRepository extends JpaRepository<HospitalService, Long> {
    List<HospitalService> findAllByTenantIdAndActiveTrue(Long tenantId);
    Optional<HospitalService> findByServiceCodeAndTenantId(String serviceCode, Long tenantId);
}
