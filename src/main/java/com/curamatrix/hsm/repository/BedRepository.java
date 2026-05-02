package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Bed;
import com.curamatrix.hsm.enums.BedStatus;
import com.curamatrix.hsm.enums.BedType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BedRepository extends JpaRepository<Bed, Long> {
    List<Bed> findByRoomIdAndTenantId(Long roomId, Long tenantId);
    List<Bed> findByRoomWardIdAndTenantId(Long wardId, Long tenantId);
    List<Bed> findByTenantId(Long tenantId);
    List<Bed> findByTenantIdAndStatus(Long tenantId, BedStatus status);

    /**
     * Returns all AVAILABLE beds for the tenant, optionally filtered by ward and/or room type.
     * Null parameters are treated as "no filter" (match all).
     */
    @Query("SELECT b FROM Bed b WHERE b.tenantId = :tenantId " +
           "AND b.status = com.curamatrix.hsm.enums.BedStatus.AVAILABLE " +
           "AND (:wardId IS NULL OR b.room.ward.id = :wardId) " +
           "AND (:roomType IS NULL OR b.room.roomType = :roomType)")
    List<Bed> findAvailableBeds(@Param("tenantId") Long tenantId,
                                @Param("wardId") Long wardId,
                                @Param("roomType") BedType roomType);

    List<Bed> findByRoomIdAndTenantIdAndStatus(Long roomId, Long tenantId, BedStatus status);
}
