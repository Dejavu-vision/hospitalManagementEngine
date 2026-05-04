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

    /** All blocked tokens for today (any status) */
    List<BlockedToken> findByAppointmentDateAndTenantIdOrderByTokenNumberAsc(
            LocalDate date, Long tenantId);

    /** Only BLOCKED (available) tokens for today */
    List<BlockedToken> findByAppointmentDateAndTenantIdAndStatusOrderByTokenNumberAsc(
            LocalDate date, Long tenantId, BlockedTokenStatus status);

    /** Check if a specific token number is blocked today */
    Optional<BlockedToken> findByTokenNumberAndAppointmentDateAndTenantId(
            Integer tokenNumber, LocalDate date, Long tenantId);

    /** Check if a specific token number is currently BLOCKED (available to assign) */
    Optional<BlockedToken> findByTokenNumberAndAppointmentDateAndTenantIdAndStatus(
            Integer tokenNumber, LocalDate date, Long tenantId, BlockedTokenStatus status);

    /** Auto-release all BLOCKED tokens for a given date (end-of-day cleanup) */
    @Modifying
    @Query("UPDATE BlockedToken bt SET bt.status = 'RELEASED' " +
           "WHERE bt.appointmentDate = :date AND bt.tenantId = :tenantId AND bt.status = 'BLOCKED'")
    int releaseAllBlockedForDate(@Param("date") LocalDate date, @Param("tenantId") Long tenantId);

    /** Check if a token number is blocked (to skip during auto-increment) */
    @Query("SELECT COUNT(bt) > 0 FROM BlockedToken bt " +
           "WHERE bt.tokenNumber = :tokenNumber AND bt.appointmentDate = :date " +
           "AND bt.tenantId = :tenantId AND bt.status = 'BLOCKED'")
    boolean isTokenBlocked(@Param("tokenNumber") Integer tokenNumber,
                           @Param("date") LocalDate date,
                           @Param("tenantId") Long tenantId);
}
