package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.LabResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LabResultRepository extends JpaRepository<LabResult, Long> {

    List<LabResult> findByLabTestIdAndTenantId(Long labTestId, Long tenantId);

    Optional<LabResult> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByLabTestId(Long labTestId);
}
