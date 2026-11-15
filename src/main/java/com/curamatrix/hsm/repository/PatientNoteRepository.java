package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.PatientNote;
import com.curamatrix.hsm.enums.PatientNoteType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PatientNoteRepository extends JpaRepository<PatientNote, Long> {
    List<PatientNote> findByPatientIdAndTenantIdAndNoteTypeInOrderByPinnedDescCreatedAtDesc(
            Long patientId, Long tenantId, Collection<PatientNoteType> types);
    Optional<PatientNote> findByIdAndPatientIdAndTenantId(Long id, Long patientId, Long tenantId);
}
