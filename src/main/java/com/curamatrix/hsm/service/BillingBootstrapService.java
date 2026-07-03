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
        log.info("Seeding default billing services for tenant {}", tenantId);

        // ── Registration Fee ────────────────────────────────────────────────
        upsertService(tenantId, "REG_FEE", "Patient Registration / Case Paper", 50, BillingItemType.REGISTRATION);

        // ── Consultation Fee (generic fallback) ─────────────────────────────
        upsertService(tenantId, "CONSULT", "General Consultation", 300, BillingItemType.CONSULTATION);

        // ── Bed Charges (one per room type) ─────────────────────────────────
        upsertService(tenantId, "BED_CHARGE_GENERAL",      "Bed Charge - General Ward",   500,   BillingItemType.BED_CHARGE);
        upsertService(tenantId, "BED_CHARGE_SEMI_PRIVATE",  "Bed Charge - Semi Private",   1000,  BillingItemType.BED_CHARGE);
        upsertService(tenantId, "BED_CHARGE_PRIVATE",       "Bed Charge - Private Room",   2000,  BillingItemType.BED_CHARGE);
        upsertService(tenantId, "BED_CHARGE_DELUXE",        "Bed Charge - Deluxe Room",    5000,  BillingItemType.BED_CHARGE);
        upsertService(tenantId, "BED_CHARGE_ICU",           "Bed Charge - ICU",            8000,  BillingItemType.BED_CHARGE);
        upsertService(tenantId, "BED_CHARGE_NICU",          "Bed Charge - NICU",           10000, BillingItemType.BED_CHARGE);
        upsertService(tenantId, "BED_CHARGE_EMERGENCY",     "Bed Charge - Emergency",      3000,  BillingItemType.BED_CHARGE);

        // ── Daily Add-on Charges ────────────────────────────────────────────
        upsertService(tenantId, "NURSING_CHARGE", "Daily Nursing Charge", 200, BillingItemType.NURSING_CHARGE);
        upsertService(tenantId, "DIET_CHARGE",    "Daily Diet Charge",    150, BillingItemType.DIET_CHARGE);

        log.info("Default billing services seeded for tenant {}", tenantId);
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
                .effectiveFrom(java.time.LocalDate.now())
                .active(true)
                .build();
        service.setTenantId(tenantId);
        hospitalServiceRepository.save(service);
    }
}
