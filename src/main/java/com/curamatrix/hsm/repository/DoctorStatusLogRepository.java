package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.DoctorStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DoctorStatusLogRepository extends JpaRepository<DoctorStatusLog, Long> {
}
