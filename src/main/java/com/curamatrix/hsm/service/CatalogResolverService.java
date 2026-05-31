package com.curamatrix.hsm.service;

import com.curamatrix.hsm.entity.HospitalService;
import com.curamatrix.hsm.enums.BedType;
import com.curamatrix.hsm.enums.BillingItemType;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.HospitalServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CatalogResolverService {

    private final HospitalServiceRepository hospitalServiceRepository;

    /**
     * Resolves REGISTRATION fee for a specific department.
     * Priority: department-specific REGISTRATION service → global REG_FEE service.
     * Returns null if neither is configured.
     */
    public HospitalService resolveRegistrationFee(Long departmentId, Long tenantId) {
        // 1st: Look for a REGISTRATION service linked to this department
        if (departmentId != null) {
            Optional<HospitalService> deptReg = hospitalServiceRepository
                    .findAllByItemTypeAndTenantIdAndActiveTrue(BillingItemType.REGISTRATION, tenantId)
                    .stream()
                    .filter(s -> s.getDepartment() != null && s.getDepartment().getId().equals(departmentId))
                    .findFirst();
            if (deptReg.isPresent()) {
                log.debug("Resolved department-specific registration fee for dept {} tenant {}", departmentId, tenantId);
                return deptReg.get();
            }
        }
        // 2nd: Fall back to global REG_FEE
        HospitalService global = resolveRequired("REG_FEE", tenantId);
        if (global != null) {
            log.debug("Resolved global REG_FEE for tenant {}", tenantId);
        }
        return global;
    }

    /**
     * Resolves a required service by code. Returns null if not found (caller decides behavior).
     */
    public HospitalService resolveRequired(String serviceCode, Long tenantId) {
        return hospitalServiceRepository.findByServiceCodeAndTenantIdAndActiveTrue(serviceCode, tenantId)
                .orElse(null);
    }

    /**
     * Resolves BED_CHARGE for a specific room type.
     * Convention: service code = "BED_CHARGE_{ROOM_TYPE}" (e.g., BED_CHARGE_ICU)
     */
    public HospitalService resolveBedCharge(BedType roomType, Long tenantId) {
        String serviceCode = "BED_CHARGE_" + roomType.name();
        return hospitalServiceRepository.findByServiceCodeAndTenantIdAndActiveTrue(serviceCode, tenantId)
                .orElseThrow(() -> {
                    log.error("Bed charge service '{}' not configured for tenant {}", serviceCode, tenantId);
                    return new ResourceNotFoundException(
                            "Bed charge service (" + serviceCode + ") is not configured for this hospital. " +
                            "Please add a BED_CHARGE service for room type '" + roomType.name() + "' in the Service Catalog.");
                });
    }

    /**
     * Resolves CONSULTATION fee: first by department, then fallback to generic "CONSULT" code.
     * Throws if neither exists.
     */
    public HospitalService resolveConsultation(Long departmentId, Long tenantId) {
        // 1st: Look for CONSULTATION service linked to the department
        if (departmentId != null) {
            Optional<HospitalService> deptService = hospitalServiceRepository
                    .findAllByItemTypeAndTenantIdAndActiveTrue(BillingItemType.CONSULTATION, tenantId)
                    .stream()
                    .filter(s -> s.getDepartment() != null && s.getDepartment().getId().equals(departmentId))
                    .findFirst();
            if (deptService.isPresent()) {
                log.debug("Resolved consultation fee from department {} for tenant {}", departmentId, tenantId);
                return deptService.get();
            }
        }

        // 2nd: Fallback to generic "CONSULT" code
        Optional<HospitalService> generic = hospitalServiceRepository
                .findByServiceCodeAndTenantIdAndActiveTrue("CONSULT", tenantId);
        if (generic.isPresent()) {
            log.debug("Resolved generic CONSULT fee for tenant {}", tenantId);
            return generic.get();
        }

        log.error("No consultation fee configured for department {} or generic CONSULT for tenant {}", departmentId, tenantId);
        throw new ResourceNotFoundException(
                "Consultation fee service is not configured for this department or as generic 'CONSULT'. " +
                "Please add it in the Service Catalog.");
    }

    /**
     * Resolves an optional service. Returns Optional.empty() if not configured.
     */
    public Optional<HospitalService> resolveOptional(String serviceCode, Long tenantId) {
        Optional<HospitalService> service = hospitalServiceRepository
                .findByServiceCodeAndTenantIdAndActiveTrue(serviceCode, tenantId);
        if (service.isEmpty()) {
            log.info("Optional service '{}' not configured for tenant {} — skipping", serviceCode, tenantId);
        }
        return service;
    }
}
