package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.VitalSign;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VitalSignRepository extends JpaRepository<VitalSign, Long> {
    List<VitalSign> findByIpdAdmissionIdAndTenantIdOrderByRecordedAtDesc(Long ipdAdmissionId, Long tenantId);
}
