package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.BedAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BedAllocationRepository extends JpaRepository<BedAllocation, Long> {
    List<BedAllocation> findByAdmissionIdAndTenantIdOrderByStartTimeDesc(Long admissionId, Long tenantId);
    Optional<BedAllocation> findByAdmissionIdAndIsCurrentTrueAndTenantId(Long admissionId, Long tenantId);
    Optional<BedAllocation> findByBedIdAndIsCurrentTrueAndTenantId(Long bedId, Long tenantId);

    List<BedAllocation> findAllByIsCurrentTrue();
    List<BedAllocation> findAllByIsCurrentTrueAndTenantId(Long tenantId);
}
