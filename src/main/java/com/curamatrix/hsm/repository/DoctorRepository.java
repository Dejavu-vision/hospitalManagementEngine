package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    Optional<Doctor> findByUserId(Long userId);

    List<Doctor> findByDepartmentId(Long departmentId);

    @Query(value = "SELECT d.* FROM doctors d JOIN users u ON d.user_id = u.id WHERE u.tenant_id = :tenantId", nativeQuery = true)
    List<Doctor> findByTenantId(@Param("tenantId") Long tenantId);
}
