package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.WalkInTokenSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface WalkInTokenSequenceRepository extends JpaRepository<WalkInTokenSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalkInTokenSequence w WHERE w.doctor.id = :doctorId " +
           "AND w.appointmentDate = :date AND w.tenantId = :tenantId")
    Optional<WalkInTokenSequence> findForUpdate(@Param("doctorId") Long doctorId,
                                                @Param("date") LocalDate date,
                                                @Param("tenantId") Long tenantId);
}
