package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.LabTest;
import com.curamatrix.hsm.enums.TestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LabTestRepository extends JpaRepository<LabTest, Long> {

    Optional<LabTest> findByIdAndTenantId(Long id, Long tenantId);

    List<LabTest> findByTestDateAndTenantIdOrderByCreatedAtAsc(LocalDate testDate, Long tenantId);

    @Query("SELECT lt FROM LabTest lt WHERE lt.testDate = :date AND lt.tenantId = :tenantId " +
           "AND lt.status = :status ORDER BY lt.createdAt ASC")
    List<LabTest> findByTestDateAndTenantIdAndStatus(@Param("date") LocalDate date,
                                                      @Param("tenantId") Long tenantId,
                                                      @Param("status") TestStatus status);

    @Query("SELECT lt.status, COUNT(lt) FROM LabTest lt WHERE lt.testDate = :date " +
           "AND lt.tenantId = :tenantId GROUP BY lt.status")
    List<Object[]> countByStatusForDate(@Param("date") LocalDate date,
                                        @Param("tenantId") Long tenantId);

    boolean existsByLabServiceId(Long labServiceId);

}
