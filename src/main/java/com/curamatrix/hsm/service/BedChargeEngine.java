package com.curamatrix.hsm.service;

import com.curamatrix.hsm.entity.BedAllocation;
import com.curamatrix.hsm.entity.Billing;
import com.curamatrix.hsm.entity.BillingItem;
import com.curamatrix.hsm.enums.BedStatus;
import com.curamatrix.hsm.enums.BillingItemType;
import com.curamatrix.hsm.enums.PaymentStatus;
import com.curamatrix.hsm.repository.BedAllocationRepository;
import com.curamatrix.hsm.repository.BillingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BedChargeEngine {

    private final BedAllocationRepository bedAllocationRepository;
    private final BillingRepository billingRepository;

    /**
     * Runs automatically at midnight every day.
     * Sweeps through all occupied beds and calculates the daily charge.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void runDailyBedChargeSweep() {
        log.info("Starting Daily Bed Charge Sweep...");
        int count = processActiveBeds();
        log.info("Daily Bed Charge Sweep completed. Billed {} active allocations.", count);
    }

    /**
     * Manual trigger for testing or admin override.
     */
    @Transactional
    public int triggerManualSweep(Long tenantId) {
        log.info("Manual Bed Charge Sweep triggered for tenant {}", tenantId);
        return processActiveBedsForTenant(tenantId);
    }

    private int processActiveBeds() {
        // Find all currently active bed allocations
        List<BedAllocation> activeAllocations = bedAllocationRepository.findAllByIsCurrentTrue();
        int count = 0;

        for (BedAllocation allocation : activeAllocations) {
            processSingleAllocation(allocation);
            count++;
        }
        return count;
    }

    private int processActiveBedsForTenant(Long tenantId) {
        List<BedAllocation> activeAllocations = bedAllocationRepository.findAllByIsCurrentTrueAndTenantId(tenantId);
        int count = 0;

        for (BedAllocation allocation : activeAllocations) {
            processSingleAllocation(allocation);
            count++;
        }
        return count;
    }

    private void processSingleAllocation(BedAllocation allocation) {
        if (allocation.getAdmission() == null || allocation.getBed().getStatus() != BedStatus.OCCUPIED) {
            return; // Skip if no admission is tied or bed isn't occupied
        }

        // 1. Find the active Running Bill (Billing) for this admission
        // Since it's @OneToOne, we just find the billing mapped.
        Billing runningBill = billingRepository.findByIpdAdmissionId(allocation.getAdmission().getId()).orElse(null);
        if (runningBill == null || runningBill.getPaymentStatus() == PaymentStatus.PAID) {
            log.warn("Skipping charge for Admission {}, no open running bill found.", allocation.getAdmission().getId());
            return;
        }

        // 2. Add New Billing Item representing today's Bed Charge
        BigDecimal dailyPrice = allocation.getDailyPriceAtTime();
        if (dailyPrice == null) {
            dailyPrice = allocation.getBed().getDailyPrice();
        }

        BillingItem chargeItem = BillingItem.builder()
                .description("Daily Bed Charge (" + allocation.getBed().getBedNumber() + ") on " + LocalDate.now())
                .amount(dailyPrice)
                .quantity(1)
                .itemType(BillingItemType.BED_CHARGE)
                .billing(runningBill)
                .build();
        
        runningBill.getItems().add(chargeItem);

        // 3. Update the Running Bill Totals
        BigDecimal newTotal = runningBill.getTotalAmount().add(dailyPrice);
        runningBill.setTotalAmount(newTotal);
        
        BigDecimal netAmount = newTotal.subtract(runningBill.getDiscount()).add(runningBill.getTax());
        runningBill.setNetAmount(netAmount);

        // 4. Save
        billingRepository.save(runningBill);
        log.info("Added Bed Charge {} to Admission {}", dailyPrice, allocation.getAdmission().getId());
    }
}
