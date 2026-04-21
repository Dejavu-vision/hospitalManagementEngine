package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.DailyProgressNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DailyProgressNoteRepository extends JpaRepository<DailyProgressNote, Long> {
    List<DailyProgressNote> findByIpdAdmissionIdAndTenantIdOrderByNoteTimeDesc(Long ipdAdmissionId, Long tenantId);
}
