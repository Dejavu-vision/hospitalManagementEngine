package com.curamatrix.hsm.service;

import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.BillingItemType;
import com.curamatrix.hsm.enums.PaymentMethod;
import com.curamatrix.hsm.enums.PaymentStatus;
import com.curamatrix.hsm.repository.BillingRepository;
import com.curamatrix.hsm.repository.HospitalServiceRepository;
import com.curamatrix.hsm.repository.PatientRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final BillingRepository billingRepository;
    private final HospitalServiceRepository hospitalServiceRepository;
    private final PatientRegistrationRepository patientRegistrationRepository;

    @Transactional(readOnly = true)
    public boolean isRegistrationValid(Long patientId, Long tenantId) {
        Optional<PatientRegistration> latest = patientRegistrationRepository.findLatestActiveRegistration(patientId, tenantId);
        return latest.isPresent() && !latest.get().isExpired();
    }

    @Transactional
    public Billing createAppointmentBilling(Appointment appointment, boolean payNow) {
        Patient patient = appointment.getPatient();
        Long tenantId = appointment.getTenantId();
        
        List<BillingItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        // 1. Check for Case Paper (Registration)
        if (!isRegistrationValid(patient.getId(), tenantId)) {
            Optional<HospitalService> regService = hospitalServiceRepository.findByServiceCodeAndTenantId("REG_FEE", tenantId);
            if (regService.isPresent()) {
                BillingItem regItem = BillingItem.builder()
                        .description("Patient Registration / Case Paper")
                        .amount(regService.get().getPrice())
                        .itemType(BillingItemType.REGISTRATION)
                        .quantity(1)
                        .build();
                items.add(regItem);
                totalAmount = totalAmount.add(regItem.getAmount());
            }
        }

        // 2. Add Consultation Fee
        // In a real system, this might depend on the doctor's specialty or rank.
        // For now, we'll assume a standard consultation service.
        Optional<HospitalService> consultService = hospitalServiceRepository.findByServiceCodeAndTenantId("CONSULT", tenantId);
        if (consultService.isPresent()) {
            BillingItem consultItem = BillingItem.builder()
                    .description("Consultation - " + appointment.getDoctor().getUser().getFullName())
                    .amount(consultService.get().getPrice())
                    .itemType(BillingItemType.CONSULTATION)
                    .quantity(1)
                    .build();
            items.add(consultItem);
            totalAmount = totalAmount.add(consultItem.getAmount());
        }

        Billing billing = Billing.builder()
                .appointment(appointment)
                .patient(patient)
                .invoiceNumber("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .totalAmount(totalAmount)
                .netAmount(totalAmount)
                .paymentStatus(payNow ? PaymentStatus.PAID : PaymentStatus.PENDING)
                .items(items)
                .build();
        
        billing.setTenantId(tenantId);
        items.forEach(item -> item.setBilling(billing));

        Billing savedBilling = billingRepository.save(billing);

        // 3. If registration was part of the bill, update patient registration
        boolean hasRegistration = items.stream().anyMatch(i -> i.getItemType() == BillingItemType.REGISTRATION);
        if (hasRegistration && payNow) {
            issueNewCasePaper(patient, savedBilling, tenantId);
        }

        return savedBilling;
    }

    /**
     * Create a standalone registration billing (case paper) for a patient — 
     * used when patient registers and pays before booking an appointment.
     */
    @Transactional
    public Billing createRegistrationBilling(Patient patient, Long tenantId, String paymentMethodStr) {
        // Check if registration is already valid
        if (isRegistrationValid(patient.getId(), tenantId)) {
            throw new RuntimeException("Patient already has a valid registration / case paper");
        }

        Optional<HospitalService> regService = hospitalServiceRepository.findByServiceCodeAndTenantId("REG_FEE", tenantId);
        if (regService.isEmpty()) {
            throw new RuntimeException("Registration fee service not configured");
        }

        BigDecimal amount = regService.get().getPrice();
        BillingItem regItem = BillingItem.builder()
                .description("Patient Registration / Case Paper")
                .amount(amount)
                .itemType(BillingItemType.REGISTRATION)
                .quantity(1)
                .build();

        PaymentMethod paymentMethod = null;
        if (paymentMethodStr != null) {
            paymentMethod = PaymentMethod.valueOf(paymentMethodStr.toUpperCase());
        }

        Billing billing = Billing.builder()
                .patient(patient)
                .invoiceNumber("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .totalAmount(amount)
                .netAmount(amount)
                .paymentStatus(PaymentStatus.PAID)
                .paymentMethod(paymentMethod)
                .items(List.of(regItem))
                .build();

        billing.setTenantId(tenantId);
        regItem.setBilling(billing);

        Billing savedBilling = billingRepository.save(billing);
        issueNewCasePaper(patient, savedBilling, tenantId);

        return savedBilling;
    }

    private void issueNewCasePaper(Patient patient, Billing billing, Long tenantId) {
        int validityDays = 30; // default
        Optional<HospitalService> regService = hospitalServiceRepository.findByServiceCodeAndTenantId("REG_FEE", tenantId);
        if (regService.isPresent() && regService.get().getValidityPeriodDays() != null) {
            validityDays = regService.get().getValidityPeriodDays();
        }

        PatientRegistration registration = PatientRegistration.builder()
                .patient(patient)
                .billing(billing)
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(validityDays))
                .active(true)
                .build();
        registration.setTenantId(tenantId);
        patientRegistrationRepository.save(registration);
    }
    
    @Transactional
    public void markAsPaid(Long billingId, Long tenantId) {
        Billing billing = billingRepository.findById(billingId)
                .orElseThrow(() -> new RuntimeException("Billing not found"));
        
        if (billing.getPaymentStatus() != PaymentStatus.PAID) {
            billing.setPaymentStatus(PaymentStatus.PAID);
            billingRepository.save(billing);
            
            // If registration was pending, activate it
            boolean hasRegistration = billing.getItems().stream()
                    .anyMatch(i -> i.getItemType() == BillingItemType.REGISTRATION);
            if (hasRegistration) {
                issueNewCasePaper(billing.getPatient(), billing, tenantId);
            }
        }
    }
}

