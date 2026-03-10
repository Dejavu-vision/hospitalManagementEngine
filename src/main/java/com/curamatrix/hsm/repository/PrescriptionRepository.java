package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {
    List<Prescription> findByDiagnosisId(Long diagnosisId);
}
