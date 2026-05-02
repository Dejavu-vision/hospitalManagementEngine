package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.IpdAdmissionSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IpdAdmissionSequenceRepository extends JpaRepository<IpdAdmissionSequence, Long> {

    /**
     * Fetches the sequence row for the given year and tenant with a pessimistic write lock.
     * This guarantees that concurrent admission requests are serialised at the DB level,
     * preventing duplicate admission numbers.
     */
    @Query(value = "SELECT * FROM ipd_admission_sequence WHERE year = :year AND tenant_id = :tenantId LIMIT 1 FOR UPDATE",
           nativeQuery = true)
    Optional<IpdAdmissionSequence> findForUpdate(@Param("year") int year,
                                                  @Param("tenantId") Long tenantId);
}
