package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.UserAccessAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAccessAuditRepository extends JpaRepository<UserAccessAudit, Long> {
    List<UserAccessAudit> findByTargetUserIdAndTenantIdOrderByCreatedAtDesc(Long targetUserId, Long tenantId);
}
