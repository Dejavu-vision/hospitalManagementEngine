package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByWardIdAndTenantId(Long wardId, Long tenantId);
    List<Room> findByTenantId(Long tenantId);
}
