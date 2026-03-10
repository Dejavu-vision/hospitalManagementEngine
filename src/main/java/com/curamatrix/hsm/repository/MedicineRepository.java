package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Medicine;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MedicineRepository extends JpaRepository<Medicine, Long> {

    /**
     * Fast search across name, generic_name, and brand fields.
     * Uses indexes on name and generic_name for optimal performance.
     * Only returns active medicines.
     */
    @Query(value = """
        SELECT DISTINCT m FROM Medicine m
        WHERE m.isActive = true
        AND (
            LOWER(m.name) LIKE LOWER(CONCAT(:query, '%'))
            OR LOWER(m.genericName) LIKE LOWER(CONCAT(:query, '%'))
            OR LOWER(m.brand) LIKE LOWER(CONCAT(:query, '%'))
        )
        ORDER BY 
            CASE 
                WHEN LOWER(m.name) LIKE LOWER(CONCAT(:query, '%')) THEN 1
                WHEN LOWER(m.genericName) LIKE LOWER(CONCAT(:query, '%')) THEN 2
                ELSE 3
            END,
            m.name ASC
        """)
    List<Medicine> searchMedicines(@Param("query") String query, Pageable pageable);

    // Legacy method - kept for backward compatibility
    List<Medicine> findByNameStartingWithIgnoreCase(String name, Pageable pageable);
}
