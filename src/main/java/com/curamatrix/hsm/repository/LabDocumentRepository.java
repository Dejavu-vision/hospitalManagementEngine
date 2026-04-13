package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.LabDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LabDocumentRepository extends JpaRepository<LabDocument, Long> {

    List<LabDocument> findByLabTestIdAndTenantId(Long labTestId, Long tenantId);

    Optional<LabDocument> findByIdAndTenantId(Long id, Long tenantId);
}
