package com.curamatrix.hsm.service;

import com.curamatrix.hsm.entity.Billing;
import com.curamatrix.hsm.entity.InsurancePolicy;
import com.curamatrix.hsm.entity.Patient;
import com.curamatrix.hsm.entity.Payment;
import com.curamatrix.hsm.entity.BillAllocation;
import com.curamatrix.hsm.repository.BillingRepository;
import com.curamatrix.hsm.repository.BillAllocationRepository;
import com.curamatrix.hsm.repository.PaymentRepository;
import com.curamatrix.hsm.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCalculationService {

    private final BillingRepository billingRepository;
    private final BillAllocationRepository billAllocationRepository;
    private final PaymentRepository paymentRepository;
    private final PatientFinancialAccountService accountService;

    /**
     * Calculates the insurance split for a given bill based on the patient's insurance profile.
     * This logic determines how much the patient owes vs how much is adjusted.
     */
    public BigDecimal calculateInsuranceAdjustment(Billing bill, InsurancePolicy profile) {
        if (profile == null) {
            return BigDecimal.ZERO;
        }
        
        // Example Rule: if cashless is eligible, we calculate the adjustment based on copay
        // If copay is 10%, the patient owes 10% of gross, insurance covers 90%.
        BigDecimal gross = bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO;
        
        if (profile.getCopayPct() != null
                && profile.getCopayPct().compareTo(BigDecimal.ZERO) >= 0
                && profile.getCopayPct().compareTo(BigDecimal.valueOf(100)) <= 0) {
            BigDecimal copayDecimal = profile.getCopayPct().divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal patientLiability = gross.multiply(copayDecimal);
            return gross.subtract(patientLiability);
        }
        
        // If no copay defined, assume full coverage up to the gross amount (simplified)
        return gross;
    }

    /**
     * Calculates the remaining balance due for a single bill.
     */
    public BigDecimal calculateBalance(Billing bill, InsurancePolicy profile) {
        BigDecimal gross = bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal discount = bill.getDiscount() != null ? bill.getDiscount() : BigDecimal.ZERO;
        BigDecimal paid = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
        
        BigDecimal insuranceAdj = calculateInsuranceAdjustment(bill, profile);
        
        BigDecimal netPayable = gross.subtract(discount).subtract(insuranceAdj);
        if (netPayable.compareTo(BigDecimal.ZERO) < 0) {
            netPayable = BigDecimal.ZERO;
        }
        
        BigDecimal balance = netPayable.subtract(paid);
        return balance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : balance;
    }

    /**
     * Allocates a lump sum payment across multiple bills (oldest-first logic).
     */
    @Transactional
    public Payment allocatePayment(Payment payment, List<Billing> pendingBills, InsurancePolicy profile) {
        BigDecimal remainingAmount = payment.getAmount();
        Long tenantId = com.curamatrix.hsm.context.TenantContext.getTenantId();
        
        payment.setTenantId(tenantId);
        Payment savedPayment = paymentRepository.save(payment);

        for (Billing bill : pendingBills) {
            if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal balanceDue = calculateBalance(bill, profile);
            if (balanceDue.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal allocationAmount = remainingAmount.min(balanceDue);
            
            // Create allocation
            BillAllocation allocation = BillAllocation.builder()
                    .payment(savedPayment)
                    .billing(bill)
                    .allocatedAmount(allocationAmount)
                    .build();
            allocation.setTenantId(tenantId);
            billAllocationRepository.save(allocation);

            // Update bill paid amount
            BigDecimal currentPaid = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
            bill.setPaidAmount(currentPaid.add(allocationAmount));
            
            // State Machine Transition
            BigDecimal newBalanceDue = calculateBalance(bill, profile);
            if (newBalanceDue.compareTo(BigDecimal.ZERO) <= 0) {
                bill.setPaymentStatus(PaymentStatus.PAID);
            } else {
                bill.setPaymentStatus(PaymentStatus.PARTIAL);
            }
            
            billingRepository.save(bill);
            remainingAmount = remainingAmount.subtract(allocationAmount);
        }

        // Trigger master account recalculation
        accountService.recalculateAccountStatus(payment.getPatient(), tenantId);

        return savedPayment;
    }
}
