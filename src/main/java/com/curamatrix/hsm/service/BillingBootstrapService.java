package com.curamatrix.hsm.service;

import com.curamatrix.hsm.entity.HospitalService;
import com.curamatrix.hsm.entity.Tenant;
import com.curamatrix.hsm.enums.BillingItemType;
import com.curamatrix.hsm.repository.HospitalServiceRepository;
import com.curamatrix.hsm.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingBootstrapService {

    private final HospitalServiceRepository hospitalServiceRepository;
    private final TenantRepository tenantRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrapBillingServices() {
        List<Tenant> tenants = tenantRepository.findAll();
        for (Tenant tenant : tenants) {
            try {
                seedDefaultServices(tenant.getId());
            } catch (Exception e) {
                log.warn("Bootstrap skipped for tenant {}: {}", tenant.getId(), e.getMessage());
            }
        }
        log.info("Billing-services bootstrap completed");
    }

    private void seedDefaultServices(Long tenantId) {
        upsertService(tenantId, "REG_FEE", "Registration / Case Paper Fee", 100.0, BillingItemType.REGISTRATION);
        upsertService(tenantId, "CONSULT", "Consultation Fee", 300.0, BillingItemType.CONSULTATION);
    }

    private void upsertService(Long tenantId, String code, String name, double price, BillingItemType type) {
        // Only create if it doesn't already exist for this tenant
        if (hospitalServiceRepository.findByServiceCodeAndTenantId(code, tenantId).isPresent()) {
            return; // Already exists, skip
        }

        HospitalService service = HospitalService.builder()
                .serviceCode(code)
                .serviceName(name)
                .price(BigDecimal.valueOf(price))
                .itemType(type)
                .active(true)
                .build();
        service.setTenantId(tenantId);
        hospitalServiceRepository.save(service);
    }
}
