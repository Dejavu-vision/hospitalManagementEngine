package com.curamatrix.hsm.service;

import com.curamatrix.hsm.entity.EmployeeIdSequence;
import com.curamatrix.hsm.enums.RoleName;
import com.curamatrix.hsm.repository.EmployeeIdSequenceRepository;
import com.curamatrix.hsm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeIdGeneratorService {

    private static final Map<RoleName, String> ROLE_PREFIX_MAP = Map.of(
            RoleName.ROLE_ADMIN, "ADM",
            RoleName.ROLE_DOCTOR, "DOC",
            RoleName.ROLE_RECEPTIONIST, "REC"
    );

    private static final int MIN_DIGITS = 3;
    private static final int MAX_INIT_RETRIES = 3;

    private final EmployeeIdSequenceRepository sequenceRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateEmployeeId(Long tenantId, RoleName roleName) {
        Objects.requireNonNull(tenantId, "tenantId must not be null for employee ID generation");

        String prefix = ROLE_PREFIX_MAP.get(roleName);
        if (prefix == null) {
            throw new IllegalArgumentException("No employee-ID prefix configured for role: " + roleName);
        }

        long serial = allocateNextSerial(tenantId, prefix);
        String employeeId = formatEmployeeId(prefix, serial);

        if (userRepository.existsByEmployeeIdAndTenantId(employeeId, tenantId)) {
            log.warn("Generated employeeId {} already exists for tenant {}. Advancing sequence.", employeeId, tenantId);
            employeeId = advanceUntilFree(tenantId, prefix);
        }

        log.info("Generated employeeId={} for tenant={}, role={}", employeeId, tenantId, roleName);
        return employeeId;
    }

    private long allocateNextSerial(Long tenantId, String prefix) {
        for (int attempt = 1; attempt <= MAX_INIT_RETRIES; attempt++) {
            EmployeeIdSequence seq = sequenceRepository.findForUpdate(tenantId, prefix).orElse(null);
            if (seq != null) {
                long serial = seq.getNextNumber();
                seq.setNextNumber(serial + 1);
                sequenceRepository.saveAndFlush(seq);
                return serial;
            }

            try {
                EmployeeIdSequence newSeq = EmployeeIdSequence.builder()
                        .tenantId(tenantId)
                        .prefix(prefix)
                        .nextNumber(2L)
                        .build();
                sequenceRepository.saveAndFlush(newSeq);
                return 1L;
            } catch (DataIntegrityViolationException ex) {
                if (attempt == MAX_INIT_RETRIES) {
                    throw new IllegalStateException("Failed to initialize employee-ID sequence for tenant "
                            + tenantId + " and prefix " + prefix, ex);
                }
                log.debug("Concurrent sequence init for tenant={} prefix={}, retry {}/{}",
                        tenantId, prefix, attempt, MAX_INIT_RETRIES);
            }
        }
        throw new IllegalStateException("Employee ID generation failed unexpectedly");
    }

    private String advanceUntilFree(Long tenantId, String prefix) {
        for (int i = 0; i < 100; i++) {
            long nextSerial = allocateNextSerial(tenantId, prefix);
            String candidate = formatEmployeeId(prefix, nextSerial);
            if (!userRepository.existsByEmployeeIdAndTenantId(candidate, tenantId)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to find free employee ID for tenant " + tenantId + " and prefix " + prefix);
    }

    private static String formatEmployeeId(String prefix, long serial) {
        int width = Math.max(MIN_DIGITS, String.valueOf(serial).length());
        return prefix + String.format("%0" + width + "d", serial);
    }
}
