package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findByIsActiveTrue();
    List<Department> findByTenantIdAndIsActiveTrue(Long tenantId);
    List<Department> findByTenantId(Long tenantId);
    Optional<Department> findByIdAndTenantId(Long id, Long tenantId);
    Optional<Department> findByNameAndTenantId(String name, Long tenantId);
}
