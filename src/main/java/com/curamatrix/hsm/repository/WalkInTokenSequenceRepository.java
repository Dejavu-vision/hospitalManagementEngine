package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.WalkInTokenSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface WalkInTokenSequenceRepository extends JpaRepository<WalkInTokenSequence, Long> {

    @Query(value = "SELECT * FROM walk_in_token_sequence WHERE appointment_date = :date AND tenant_id = :tenantId LIMIT 1",
           nativeQuery = true)
    Optional<WalkInTokenSequence> findForUpdate(@Param("date") LocalDate date,
                                                @Param("tenantId") Long tenantId);
}
