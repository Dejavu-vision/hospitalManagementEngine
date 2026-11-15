package com.curamatrix.hsm.service;

import com.curamatrix.hsm.entity.PatientAccessLog;
import com.curamatrix.hsm.repository.PatientAccessLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientAccessAuditService {

    private final PatientAccessLogRepository repository;

    /** Records a patient record access. Never throws: auditing must not block care. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long patientId, String action) {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            repository.save(PatientAccessLog.builder()
                    .patientId(patientId)
                    .userEmail(auth != null ? auth.getName() : null)
                    .userRole(PatientViewRole.current().name())
                    .action(action)
                    .accessedAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("Could not write patient access log for patient {}: {}", patientId, e.getMessage());
        }
    }
}
