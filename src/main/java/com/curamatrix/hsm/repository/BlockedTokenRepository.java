package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.BlockedToken;
import com.curamatrix.hsm.entity.BlockedToken.BlockedTokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BlockedTokenRepository extends JpaRepository<BlockedToken, Long> {

    /** All blocked tokens for today (any status) scoped to a specific doctor */
    List<BlockedToken> findByAppointmentDateAndTenantIdAndDoctorIdOrderByTokenNumberAsc(
            LocalDate date, Long tenantId, Long doctorId);

    /** Only BLOCKED (available) tokens for today scoped to a specific doctor */
    List<BlockedToken> findByAppointmentDateAndTenantIdAndDoctorIdAndStatusOrderByTokenNumberAsc(
            LocalDate date, Long tenantId, Long doctorId, BlockedTokenStatus status);

    /** Check if a specific token number is blocked today for a specific doctor */
    Optional<BlockedToken> findByTokenNumberAndAppointmentDateAndTenantIdAndDoctorId(
            Integer tokenNumber, LocalDate date, Long tenantId, Long doctorId);

    /** Check if a specific token number is currently BLOCKED (available to assign) for a specific doctor */
    Optional<BlockedToken> findByTokenNumberAndAppointmentDateAndTenantIdAndDoctorIdAndStatus(
            Integer tokenNumber, LocalDate date, Long tenantId, Long doctorId, BlockedTokenStatus status);

    /** Auto-release all BLOCKED tokens for a given date (end-of-day cleanup — no doctor filter needed) */
    @Modifying
    @Query("UPDATE BlockedToken bt SET bt.status = 'RELEASED' " +
           "WHERE bt.appointmentDate = :date AND bt.tenantId = :tenantId AND bt.status = 'BLOCKED'")
    int releaseAllBlockedForDate(@Param("date") LocalDate date, @Param("tenantId") Long tenantId);

    /** Check if a token number is blocked for a specific doctor (to skip during auto-increment) */
    @Query("SELECT COUNT(bt) > 0 FROM BlockedToken bt " +
           "WHERE bt.tokenNumber = :tokenNumber AND bt.appointmentDate = :date " +
           "AND bt.tenantId = :tenantId AND bt.doctorId = :doctorId AND bt.status = 'BLOCKED'")
    boolean isTokenBlocked(@Param("tokenNumber") Integer tokenNumber,
                           @Param("date") LocalDate date,
                           @Param("tenantId") Long tenantId,
                           @Param("doctorId") Long doctorId);
}
