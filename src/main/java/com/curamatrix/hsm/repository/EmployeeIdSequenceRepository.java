package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.EmployeeIdSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmployeeIdSequenceRepository extends JpaRepository<EmployeeIdSequence, Long> {

    /**
     * Acquires a row-level exclusive lock (SELECT … FOR UPDATE) on the
     * sequence row for the given tenant + prefix.
     *
     * <p>This guarantees that concurrent transactions block here until
     * the holder commits, so two threads never get the same number.</p>
     *
     * <p>If the row does not yet exist (first employee of that role in
     * this tenant), the Optional will be empty and the caller must
     * INSERT a new row — the unique constraint protects against
     * duplicate inserts.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM EmployeeIdSequence s WHERE s.tenantId = :tenantId AND s.prefix = :prefix")
    Optional<EmployeeIdSequence> findForUpdate(@Param("tenantId") Long tenantId,
                                                @Param("prefix") String prefix);
}
