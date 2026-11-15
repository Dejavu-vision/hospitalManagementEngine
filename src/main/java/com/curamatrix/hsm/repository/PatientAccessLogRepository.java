package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.PatientAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientAccessLogRepository extends JpaRepository<PatientAccessLog, Long> {
}
