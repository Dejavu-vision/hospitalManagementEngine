package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.ConsolidatedPaymentRequest;
import com.curamatrix.hsm.dto.response.PaymentContextDto;
import com.curamatrix.hsm.entity.Billing;
import com.curamatrix.hsm.entity.InsurancePolicy;
import com.curamatrix.hsm.entity.Patient;
import com.curamatrix.hsm.entity.Payment;
import com.curamatrix.hsm.enums.PaymentMethod;
import com.curamatrix.hsm.repository.BillingRepository;
import com.curamatrix.hsm.repository.PatientRepository;
import com.curamatrix.hsm.service.PaymentCalculationService;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PatientRepository patientRepository;
    private final BillingRepository billingRepository;
    private final PaymentCalculationService calculationService;

    @GetMapping("/context/{patientId}")
    public ResponseEntity<PaymentContextDto> getPaymentContext(
            @PathVariable Long patientId,
            @RequestParam(required = false) String entryPoint) {
            
        Long tenantId = TenantContext.getTenantId();
        
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId));
                
        // 1. Resolve Insurance Profile
        InsurancePolicy activePolicy = null;
        PaymentContextDto.InsuranceProfileContext insuranceContext = null;
        
        if (patient.getInsurancePolicies() != null && !patient.getInsurancePolicies().isEmpty()) {
            activePolicy = patient.getInsurancePolicies().get(0); // Assuming first is active for simplicity
            insuranceContext = PaymentContextDto.InsuranceProfileContext.builder()
                    .provider(activePolicy.getPayer() != null ? activePolicy.getPayer().getInsurerName() : null)
                    .policyNumber(activePolicy.getPolicyNumber())
                    .tpaName(activePolicy.getPayer() != null ? activePolicy.getPayer().getTpaName() : null)
                    .cashlessEligible(activePolicy.getCopayPct() != null && activePolicy.getCopayPct().compareTo(BigDecimal.valueOf(100)) < 0)
                    .copayPercentage(activePolicy.getCopayPct() != null ? activePolicy.getCopayPct() : BigDecimal.ZERO)
                    .preAuthStatus("not_applicable")
                    .build();
        }

        // 2. Fetch Pending Bills
        List<Billing> allBills = billingRepository.findAllByPatientIdAndTenantId(patientId, tenantId);
        List<PaymentContextDto.BillContext> billContexts = new ArrayList<>();
        BigDecimal totalBalanceDue = BigDecimal.ZERO;
        
        for (Billing bill : allBills) {
            if (bill.getPaymentStatus() == com.curamatrix.hsm.enums.PaymentStatus.CANCELLED) {
                continue;
            }
            
            BigDecimal balanceDue = calculationService.calculateBalance(bill, activePolicy);
            if (balanceDue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal gross = bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO;
                BigDecimal insuranceAdj = calculationService.calculateInsuranceAdjustment(bill, activePolicy);
                BigDecimal paid = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
                
                String desc = bill.getAppointment() != null ? "OPD" : (bill.getIpdAdmission() != null ? "IPD" : "OPD");
                
                PaymentContextDto.BillContext bc = PaymentContextDto.BillContext.builder()
                        .billId(bill.getId())
                        .visitDate(bill.getCreatedAt() != null ? bill.getCreatedAt().toLocalDate().toString() : "")
                        .description(desc)
                        .grossAmount(gross)
                        .insuranceAdjustment(insuranceAdj)
                        .netPayable(gross.subtract(insuranceAdj))
                        .amountAlreadyPaid(paid)
                        .balanceDue(balanceDue)
                        .build();
                        
                billContexts.add(bc);
                totalBalanceDue = totalBalanceDue.add(balanceDue);
            }
        }
        
        // 3. Build Context
        PaymentContextDto context = PaymentContextDto.builder()
                .patientId(patient.getId())
                .patientName(patient.getFirstName() + " " + patient.getLastName())
                .bills(billContexts)
                .totalBalanceDue(totalBalanceDue)
                .insuranceProfile(insuranceContext)
                .entryPoint(entryPoint != null ? entryPoint : "pending_bill")
                .build();
                
        return ResponseEntity.ok(context);
    }

    @PostMapping
    public ResponseEntity<Void> processConsolidatedPayment(@RequestBody ConsolidatedPaymentRequest request) {
        Long tenantId = TenantContext.getTenantId();
        
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", request.getPatientId()));
                
        // Fetch pending bills in oldest-first order (by billing_date)
        List<Billing> pendingBills = billingRepository.findAllByPatientIdAndTenantId(patient.getId(), tenantId);
        // Note: Ideally, the repository method should order by billingDate ASC.
        
        InsurancePolicy activePolicy = null;
        if (patient.getInsurancePolicies() != null && !patient.getInsurancePolicies().isEmpty()) {
            activePolicy = patient.getInsurancePolicies().get(0);
        }
        
        Payment payment = Payment.builder()
                .patient(patient)
                .amount(request.getAmount())
                .method(PaymentMethod.valueOf(request.getMethod()))
                .referenceNumber(request.getReferenceNumber())
                // .collectedById() could be set by looking up the current user from SecurityContext
                .build();
                
        calculationService.allocatePayment(payment, pendingBills, activePolicy);
        
        return ResponseEntity.ok().build();
    }
}
