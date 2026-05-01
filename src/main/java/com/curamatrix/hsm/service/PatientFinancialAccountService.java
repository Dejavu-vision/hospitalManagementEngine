package com.curamatrix.hsm.service;

import com.curamatrix.hsm.entity.Billing;
import com.curamatrix.hsm.entity.Patient;
import com.curamatrix.hsm.entity.PatientFinancialAccount;
import com.curamatrix.hsm.enums.FinancialStatus;
import com.curamatrix.hsm.enums.PaymentStatus;
import com.curamatrix.hsm.repository.BillingRepository;
import com.curamatrix.hsm.repository.PatientFinancialAccountRepository;
import com.curamatrix.hsm.repository.PaymentPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientFinancialAccountService {

    private final PatientFinancialAccountRepository accountRepository;
    private final BillingRepository billingRepository;
    private final PaymentPlanRepository paymentPlanRepository;

    @Transactional
    public PatientFinancialAccount getOrCreateAccount(Patient patient, Long tenantId) {
        return accountRepository.findByPatientIdAndTenantId(patient.getId(), tenantId)
                .orElseGet(() -> {
                    PatientFinancialAccount newAccount = PatientFinancialAccount.builder()
                            .patient(patient)
                            .financialStatus(FinancialStatus.REGISTERED_UNPAID)
                            .totalBilledLifetime(BigDecimal.ZERO)
                            .totalPaidLifetime(BigDecimal.ZERO)
                            .currentOutstanding(BigDecimal.ZERO)
                            .build();
                    newAccount.setTenantId(tenantId);
                    return accountRepository.save(newAccount);
                });
    }

    @Transactional
    public void recalculateAccountStatus(Patient patient, Long tenantId) {
        PatientFinancialAccount account = getOrCreateAccount(patient, tenantId);
        
        List<Billing> allBills = billingRepository.findAllByPatientIdAndTenantId(patient.getId(), tenantId);
        
        BigDecimal totalBilled = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal currentOutstanding = BigDecimal.ZERO;
        
        boolean hasUnpaid = false;
        boolean hasPartial = false;
        
        for (Billing bill : allBills) {
            if (bill.getPaymentStatus() != PaymentStatus.CANCELLED) {
                totalBilled = totalBilled.add(bill.getNetAmount() != null ? bill.getNetAmount() : BigDecimal.ZERO);
                totalPaid = totalPaid.add(bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO);
                
                BigDecimal balance = (bill.getNetAmount() != null ? bill.getNetAmount() : BigDecimal.ZERO)
                        .subtract(bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO);
                
                if (balance.compareTo(BigDecimal.ZERO) > 0) {
                    currentOutstanding = currentOutstanding.add(balance);
                    hasUnpaid = true;
                    if (bill.getPaidAmount() != null && bill.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
                        hasPartial = true;
                    }
                }
            }
        }
        
        account.setTotalBilledLifetime(totalBilled);
        account.setTotalPaidLifetime(totalPaid);
        account.setCurrentOutstanding(currentOutstanding);
        
        // Determine Financial Status
        if (account.isPaymentPlanActive()) {
            account.setFinancialStatus(FinancialStatus.PAYMENT_PLAN);
        } else if (currentOutstanding.compareTo(BigDecimal.ZERO) <= 0 && totalBilled.compareTo(BigDecimal.ZERO) > 0) {
            account.setFinancialStatus(FinancialStatus.FULLY_PAID);
        } else if (hasPartial) {
            account.setFinancialStatus(FinancialStatus.PARTIALLY_PAID);
        } else if (hasUnpaid) {
            account.setFinancialStatus(FinancialStatus.BILL_GENERATED);
        } else {
            account.setFinancialStatus(FinancialStatus.REGISTERED_UNPAID);
        }
        
        accountRepository.save(account);
        log.info("Recalculated financial account for patient {}: status={}, outstanding={}", patient.getId(), account.getFinancialStatus(), account.getCurrentOutstanding());
    }
}
