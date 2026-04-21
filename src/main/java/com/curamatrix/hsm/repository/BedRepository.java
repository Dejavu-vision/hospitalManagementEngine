package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Bed;
import com.curamatrix.hsm.enums.BedStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BedRepository extends JpaRepository<Bed, Long> {
    List<Bed> findByRoomIdAndTenantId(Long roomId, Long tenantId);
    List<Bed> findByRoomWardIdAndTenantId(Long wardId, Long tenantId);
    List<Bed> findByTenantId(Long tenantId);
    List<Bed> findByTenantIdAndStatus(Long tenantId, BedStatus status);
}
