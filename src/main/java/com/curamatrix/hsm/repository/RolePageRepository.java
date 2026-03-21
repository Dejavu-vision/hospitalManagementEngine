package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.RolePage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface RolePageRepository extends JpaRepository<RolePage, Long> {

    List<RolePage> findByRoleIdIn(Collection<Long> roleIds);

    @Query("SELECT rp.page.pageKey FROM RolePage rp WHERE rp.role.id = :roleId AND rp.page.isActive = true")
    List<String> findActivePageKeysByRoleId(Long roleId);

    void deleteByRoleId(Long roleId);
}
