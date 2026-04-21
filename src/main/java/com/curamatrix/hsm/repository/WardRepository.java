package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WardRepository extends JpaRepository<Ward, Long> {
    List<Ward> findByTenantId(Long tenantId);
}
