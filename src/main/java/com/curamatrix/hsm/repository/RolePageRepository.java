package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.RolePage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RolePageRepository extends JpaRepository<RolePage, Long> {

    List<RolePage> findByRoleId(Long roleId);

    List<RolePage> findByRoleIdIn(Collection<Long> roleIds);

    @Query("SELECT rp.page.pageKey FROM RolePage rp WHERE rp.role.id = :roleId AND rp.page.isActive = true")
    List<String> findActivePageKeysByRoleId(@Param("roleId") Long roleId);

    @Modifying
    @Query("DELETE FROM RolePage rp WHERE rp.role.id = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);
}
