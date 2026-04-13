package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.LabTestStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LabTestStatusLogRepository extends JpaRepository<LabTestStatusLog, Long> {

    List<LabTestStatusLog> findByLabTestIdAndTenantIdOrderByChangedAtDesc(Long labTestId, Long tenantId);

    @Query("SELECT l FROM LabTestStatusLog l WHERE l.tenantId = :tenantId " +
           "AND l.changedAt BETWEEN :from AND :to ORDER BY l.changedAt DESC")
    List<LabTestStatusLog> findByTenantIdAndDateRange(@Param("tenantId") Long tenantId,
                                                       @Param("from") LocalDateTime from,
                                                       @Param("to") LocalDateTime to);
}
