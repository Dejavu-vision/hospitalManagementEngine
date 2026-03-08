package com.curamatrix.hsm.repository;


import com.curamatrix.hsm.entity.Medicine;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicineRepository extends JpaRepository<Medicine, Long> {

    List<Medicine> findByNameStartingWithIgnoreCase(String name, Pageable pageable);

}
