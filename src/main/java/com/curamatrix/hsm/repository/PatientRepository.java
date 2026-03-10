package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    
    @Query("SELECT p FROM Patient p WHERE " +
           "LOWER(CONCAT(p.firstName, ' ', p.lastName)) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "p.phone LIKE CONCAT('%', :search, '%')")
    Page<Patient> searchPatients(@Param("search") String search, Pageable pageable);
    
    long countByTenantId(Long tenantId);
}
