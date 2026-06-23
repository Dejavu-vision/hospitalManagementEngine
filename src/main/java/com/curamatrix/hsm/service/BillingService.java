package com.curamatrix.hsm.service;

import com.curamatrix.hsm.dto.request.AddBillingItemRequest;
import com.curamatrix.hsm.dto.request.CollectPaymentRequest;
import com.curamatrix.hsm.dto.response.BillingItemResponse;
import com.curamatrix.hsm.dto.response.BillingResponse;
import com.curamatrix.hsm.dto.response.BillingSummaryResponse;
import com.curamatrix.hsm.dto.response.InsuranceSplitResponse;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.BillingItemType;
import com.curamatrix.hsm.enums.PaymentMethod;
import com.curamatrix.hsm.enums.PaymentStatus;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.BillingRepository;
import com.curamatrix.hsm.repository.BillInsuranceSplitRepository;
import com.curamatrix.hsm.repository.HospitalServiceRepository;
import com.curamatrix.hsm.repository.DepartmentRepository;
import com.curamatrix.hsm.repository.InsurancePolicyRepository;
import com.curamatrix.hsm.repository.PatientRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final BillingRepository billingRepository;
    private final BillInsuranceSplitRepository splitRepository;
    private final HospitalServiceRepository hospitalServiceRepository;
    private final DepartmentRepository departmentRepository;
    private final PatientRegistrationRepository patientRegistrationRepository;
    private final InsurancePolicyRepository insurancePolicyRepository;
    private final PatientFinancialAccountService accountService;
    private final CatalogResolverService catalogResolver;

    // ─── Registration Validation ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public boolean isRegistrationValid(Long patientId, Long tenantId) {
        Optional<PatientRegistration> latest = patientRegistrationRepository.findFirstByPatientIdAndTenantIdAndActiveTrueOrderByExpiresAtDesc(patientId, tenantId);
        return latest.isPresent() && !latest.get().isExpired();
    }

    // ─── Appointment Billing (existing flow — preserved) ─────────────────────

    @Transactional
    public Billing createAppointmentBilling(Appointment appointment, boolean payNow) {
        Patient patient = appointment.getPatient();
        Long tenantId = appointment.getTenantId();

        List<BillingItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        // 1. Check for Case Paper (Registration)
        if (!isRegistrationValid(patient.getId(), tenantId)) {
            HospitalService regService = catalogResolver.resolveRequired("REG_FEE", tenantId);
            BigDecimal regAmount = regService != null ? regService.getPrice() : BigDecimal.ZERO;

            if (regAmount.compareTo(BigDecimal.ZERO) > 0) {
                // Registration fee is ALWAYS patient-pay regardless of insurance
                BillingItem regItem = BillingItem.builder()
                        .description(regService.getServiceName())
                        .amount(regAmount)
                        .itemType(BillingItemType.REGISTRATION)
                        .quantity(1)
                        .insuranceCoverage(com.curamatrix.hsm.enums.InsuranceCoverage.NOT_COVERED)
                        .build();
                items.add(regItem);
                totalAmount = totalAmount.add(regAmount);
            }
        }

        // 2. Add Consultation Fee
        // Priority: Doctor's own fee (if set) > Department catalog rate > Generic CONSULT rate
        Long departmentId = appointment.getDoctor().getDepartment() != null ? appointment.getDoctor().getDepartment().getId() : null;
        BigDecimal doctorOwnFee = appointment.getDoctor().getConsultationFee();
        
        BigDecimal fee;
        String desc;
        if (doctorOwnFee != null && doctorOwnFee.compareTo(BigDecimal.ZERO) > 0) {
            // Doctor has a specific fee set — use it
            fee = doctorOwnFee;
            desc = "Consultation - " + appointment.getDoctor().getUser().getFullName();
        } else {
            // Fall back to catalog (department rate or generic CONSULT)
            try {
                HospitalService consultService = catalogResolver.resolveConsultation(departmentId, tenantId);
                fee = consultService.getPrice();
                desc = consultService.getServiceName();
            } catch (Exception e) {
                // No catalog entry and no doctor fee — charge ₹0
                fee = BigDecimal.ZERO;
                desc = "Consultation - " + appointment.getDoctor().getUser().getFullName();
            }
        }

        BillingItem consultItem = BillingItem.builder()
                .description(desc)
                .amount(fee)
                .itemType(BillingItemType.CONSULTATION)
                .quantity(1)
                .insuranceCoverage(com.curamatrix.hsm.enums.InsuranceCoverage.NOT_COVERED)
                .build();
        items.add(consultItem);
        totalAmount = totalAmount.add(consultItem.getAmount());

        BigDecimal insuranceAdjustment = BigDecimal.ZERO;
        for (BillingItem item : items) {
            if (item.getInsuranceCoverage() == com.curamatrix.hsm.enums.InsuranceCoverage.COVERED) {
                insuranceAdjustment = insuranceAdjustment.add(item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity())));
            } else if (item.getInsuranceCoverage() == com.curamatrix.hsm.enums.InsuranceCoverage.PARTIAL) {
                insuranceAdjustment = insuranceAdjustment.add(item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity())).multiply(new BigDecimal("0.5")));
            }
        }

        BigDecimal netAmount = totalAmount.subtract(insuranceAdjustment);
        if (netAmount.compareTo(BigDecimal.ZERO) < 0) netAmount = BigDecimal.ZERO;

        Billing billing = Billing.builder()
                .appointment(appointment)
                .patient(patient)
                .invoiceNumber("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .totalAmount(totalAmount)
                .insuranceAdjustment(insuranceAdjustment)
                .netAmount(netAmount)
                .paymentStatus(payNow ? PaymentStatus.PAID : PaymentStatus.PENDING)
                .paidAmount(payNow ? totalAmount : BigDecimal.ZERO)
                .paidAt(payNow ? LocalDateTime.now() : null)
                .items(items)
                .build();

        billing.setTenantId(tenantId);
        items.forEach(item -> {
            item.setBilling(billing);
            if (payNow) {
                item.setPaymentStatus(PaymentStatus.PAID);
                item.setPaidAmount(item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        });

        Billing savedBilling = billingRepository.save(billing);

        // 3. If registration was part of the bill, update patient registration
        boolean hasRegistration = items.stream().anyMatch(i -> i.getItemType() == BillingItemType.REGISTRATION);
        if (hasRegistration && payNow) {
            issueNewCasePaper(patient, savedBilling, tenantId);
        }

        accountService.recalculateAccountStatus(patient, tenantId);

        return savedBilling;
    }

    // ─── Registration Billing (existing flow — preserved) ────────────────────

    @Transactional
    public Billing createRegistrationBilling(Patient patient, Long tenantId, String paymentMethodStr) {
        // Validation removed as per request to allow re-registration even if valid paper exists
        
        // Resolve registration fee from Service Catalog (₹0 if not configured)
        HospitalService regServiceResolved = catalogResolver.resolveRequired("REG_FEE", tenantId);
        BigDecimal amount = regServiceResolved != null ? regServiceResolved.getPrice() : BigDecimal.ZERO;
        String description = regServiceResolved != null ? regServiceResolved.getServiceName() : "Registration Fee";
        BigDecimal insuranceAdjustment = BigDecimal.ZERO;

        BillingItem regItem = BillingItem.builder()
                .description(description)
                .amount(amount)
                .itemType(BillingItemType.REGISTRATION)
                .quantity(1)
                .insuranceCoverage(com.curamatrix.hsm.enums.InsuranceCoverage.NOT_COVERED)
                .build();

        PaymentMethod paymentMethod = null;
        if (paymentMethodStr != null) {
            paymentMethod = PaymentMethod.valueOf(paymentMethodStr.toUpperCase());
        }

        BigDecimal netAmount = amount.subtract(insuranceAdjustment);
        if (netAmount.compareTo(BigDecimal.ZERO) < 0) netAmount = BigDecimal.ZERO;

        Billing billing = Billing.builder()
                .patient(patient)
                .invoiceNumber("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .totalAmount(amount)
                .insuranceAdjustment(insuranceAdjustment)
                .netAmount(netAmount)
                .paymentStatus(PaymentStatus.PENDING) // Start as PENDING
                .paymentMethod(paymentMethod)
                .paidAmount(BigDecimal.ZERO)          // No payment yet
                .paidAt(null)
                .items(List.of(regItem))
                .build();

        billing.setTenantId(tenantId);
        regItem.setBilling(billing);

        Billing savedBilling = billingRepository.save(billing);
        accountService.recalculateAccountStatus(patient, tenantId);
        
        return savedBilling;
        // Note: issueNewCasePaper will be called in collectPayment() once status becomes PAID
    }

    /**
     * Issues a new case paper (PatientRegistration) for a patient.
     * Deactivates any existing active case papers first.
     * Exposed for ReceptionDeskService to call during explicit case paper creation.
     */
    public void issueNewCasePaperForPatient(Patient patient, Billing billing, Long tenantId) {
        issueNewCasePaper(patient, billing, tenantId);
    }

    private void issueNewCasePaper(Patient patient, Billing billing, Long tenantId) {
        int validityDays = 30;
        Optional<HospitalService> regService = catalogResolver.resolveOptional("REG_FEE", tenantId);
        if (regService.isPresent() && regService.get().getValidityPeriodDays() != null) {
            validityDays = regService.get().getValidityPeriodDays();
        }

        // Deactivate any existing active case papers for this patient
        patientRegistrationRepository
                .findFirstByPatientIdAndTenantIdAndActiveTrueOrderByExpiresAtDesc(patient.getId(), tenantId)
                .ifPresent(existing -> {
                    existing.setActive(false);
                    patientRegistrationRepository.save(existing);
                });

        PatientRegistration registration = PatientRegistration.builder()
                .patient(patient)
                .billing(billing)
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(validityDays).toLocalDate().atTime(23, 59, 59))
                .active(true)
                .build();
        registration.setTenantId(tenantId);
        patientRegistrationRepository.save(registration);
    }

    // ─── NEW: Get Invoice by ID ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BillingResponse getInvoiceById(Long id, Long tenantId) {
        Billing billing = billingRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Billing", "id", id));
        return mapToResponse(billing);
    }

    // ─── NEW: Get All Invoices (filtered) ────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BillingResponse> getAllInvoices(Long tenantId, String statusFilter) {
        List<Billing> billings;
        if (statusFilter != null && !statusFilter.equalsIgnoreCase("ALL")) {
            PaymentStatus status = PaymentStatus.valueOf(statusFilter.toUpperCase());
            billings = billingRepository.findAllByTenantIdAndPaymentStatusOrderByCreatedAtDesc(tenantId, status);
        } else {
            billings = billingRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        }
        return billings.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // ─── NEW: Get Patient Invoices ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BillingResponse> getPatientInvoices(Long patientId, Long tenantId) {
        List<Billing> billings = billingRepository.findAllByPatientIdAndTenantId(patientId, tenantId);
        return billings.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // ─── NEW: Collect Payment ────────────────────────────────────────────────

    @Transactional
    public BillingResponse collectPayment(Long billingId, CollectPaymentRequest request, Long tenantId) {
        Billing billing = billingRepository.findByIdAndTenantId(billingId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Billing", "id", billingId));

        if (billing.getPaymentStatus() == PaymentStatus.PAID) {
            throw new RuntimeException("Invoice is already fully paid");
        }
        if (billing.getPaymentStatus() == PaymentStatus.CANCELLED) {
            throw new RuntimeException("Cannot collect payment on a cancelled invoice");
        }

        PaymentMethod paymentMethod = PaymentMethod.valueOf(request.getPaymentMethod().toUpperCase());
        BigDecimal newPaidAmount = billing.getPaidAmount().add(request.getAmount());
        BigDecimal balance = billing.getNetAmount().subtract(newPaidAmount);

        billing.setPaymentMethod(paymentMethod);
        billing.setPaidAmount(newPaidAmount);
        billing.setRemarks(request.getRemarks());

        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            // Fully paid
            billing.setPaymentStatus(PaymentStatus.PAID);
            billing.setPaidAt(LocalDateTime.now());
            billing.setPaidAmount(billing.getNetAmount()); // cap at net
            
            // Mark all items as PAID
            billing.getItems().forEach(item -> {
                item.setPaymentStatus(PaymentStatus.PAID);
                item.setPaidAmount(item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity())));
            });

            // If registration was pending, activate case paper
            boolean hasRegistration = billing.getItems().stream()
                    .anyMatch(i -> i.getItemType() == BillingItemType.REGISTRATION);
            if (hasRegistration) {
                issueNewCasePaper(billing.getPatient(), billing, tenantId);
            }
        } else {
            // Partial payment
            billing.setPaymentStatus(PaymentStatus.PARTIAL);
            
            // Allocate sequentially
            BigDecimal unallocatedPayment = request.getAmount();
            for (BillingItem item : billing.getItems()) {
                if (unallocatedPayment.compareTo(BigDecimal.ZERO) <= 0) break;
                if (item.getPaymentStatus() == PaymentStatus.PAID) continue;
                
                BigDecimal itemTotal = item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity()));
                BigDecimal itemBalance = itemTotal.subtract(item.getPaidAmount());
                
                if (itemBalance.compareTo(BigDecimal.ZERO) > 0) {
                    if (unallocatedPayment.compareTo(itemBalance) >= 0) {
                        item.setPaidAmount(item.getPaidAmount().add(itemBalance));
                        item.setPaymentStatus(PaymentStatus.PAID);
                        unallocatedPayment = unallocatedPayment.subtract(itemBalance);
                    } else {
                        item.setPaidAmount(item.getPaidAmount().add(unallocatedPayment));
                        item.setPaymentStatus(PaymentStatus.PARTIAL);
                        unallocatedPayment = BigDecimal.ZERO;
                    }
                }
            }
        }

        billing = billingRepository.save(billing);
        log.info("Payment collected for billing {}: amount={}, method={}, status={}",
                billingId, request.getAmount(), paymentMethod, billing.getPaymentStatus());

        accountService.recalculateAccountStatus(billing.getPatient(), tenantId);

        return mapToResponse(billing);
    }

    // ─── NEW: Apply Discount ─────────────────────────────────────────────────
    
    @Transactional
    public void cancelBilling(Long billingId, Long tenantId) {
        Billing billing = billingRepository.findByIdAndTenantId(billingId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Billing", "id", billingId));

        if (billing.getPaymentStatus() == PaymentStatus.CANCELLED) {
            return;
        }

        billing.setPaymentStatus(PaymentStatus.CANCELLED);
        billing.setRemarks((billing.getRemarks() != null ? billing.getRemarks() + "\n" : "") + "Cancelled due to case paper cancellation");
        billingRepository.save(billing);
        
        accountService.recalculateAccountStatus(billing.getPatient(), tenantId);
        
        log.info("Billing {} cancelled", billingId);
    }

    @Transactional
    public BillingResponse applyDiscount(Long billingId, BigDecimal discount, Long tenantId) {
        Billing billing = billingRepository.findByIdAndTenantId(billingId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Billing", "id", billingId));

        if (billing.getPaymentStatus() == PaymentStatus.PAID) {
            throw new RuntimeException("Cannot apply discount to a fully paid invoice");
        }

        billing.setDiscount(discount);
        recalculateNetAmount(billing);
        billing = billingRepository.save(billing);

        accountService.recalculateAccountStatus(billing.getPatient(), tenantId);

        log.info("Discount applied to billing {}: discount={}, newNet={}", billingId, discount, billing.getNetAmount());
        return mapToResponse(billing);
    }

    // ─── NEW: Add Billing Item ───────────────────────────────────────────────

    @Transactional
    public BillingResponse addBillingItem(Long billingId, AddBillingItemRequest request, Long tenantId) {
        Billing billing = billingRepository.findByIdAndTenantId(billingId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Billing", "id", billingId));

        if (billing.getPaymentStatus() == PaymentStatus.PAID) {
            throw new RuntimeException("Cannot add items to a fully paid invoice");
        }

        BillingItemType itemType = BillingItemType.valueOf(request.getItemType().toUpperCase());
        int quantity = request.getQuantity() != null ? request.getQuantity() : 1;
        BigDecimal itemTotal = request.getAmount().multiply(BigDecimal.valueOf(quantity));

        BillingItem newItem = BillingItem.builder()
                .billing(billing)
                .description(request.getDescription())
                .amount(request.getAmount())
                .quantity(quantity)
                .itemType(itemType)
                .build();

        if (request.getDepartmentId() != null) {
            departmentRepository.findById(request.getDepartmentId())
                    .ifPresent(newItem::setDepartment);
        }

        if (request.getServiceCatalogItemId() != null) {
            hospitalServiceRepository.findById(request.getServiceCatalogItemId())
                    .ifPresent(newItem::setHospitalService);
        }

        boolean payNow = Boolean.TRUE.equals(request.getPayNow());
        if (payNow) {
            newItem.setPaymentStatus(PaymentStatus.PAID);
            newItem.setPaidAmount(itemTotal);
            billing.setPaidAmount(billing.getPaidAmount().add(itemTotal));
            if (request.getPaymentMethod() != null) {
                try {
                    billing.setPaymentMethod(PaymentMethod.valueOf(request.getPaymentMethod().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    billing.setPaymentMethod(PaymentMethod.CASH);
                }
            }
        } else {
            newItem.setPaymentStatus(PaymentStatus.PENDING);
            newItem.setPaidAmount(BigDecimal.ZERO);
        }

        billing.getItems().add(newItem);
        billing.setTotalAmount(billing.getTotalAmount().add(itemTotal));
        recalculateNetAmount(billing);

        if (payNow) {
            BigDecimal balance = billing.getNetAmount().subtract(billing.getPaidAmount());
            if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                billing.setPaymentStatus(PaymentStatus.PAID);
                billing.setPaidAt(LocalDateTime.now());
                billing.setPaidAmount(billing.getNetAmount());
            } else {
                billing.setPaymentStatus(PaymentStatus.PARTIAL);
            }
        }

        billing = billingRepository.save(billing);

        if (payNow) {
            boolean hasRegistration = newItem.getItemType() == BillingItemType.REGISTRATION;
            if (hasRegistration) {
                issueNewCasePaper(billing.getPatient(), billing, tenantId);
            }
        }

        accountService.recalculateAccountStatus(billing.getPatient(), tenantId);

        log.info("Item added to billing {}: desc={}, amount={}, payNow={}", billingId, request.getDescription(), request.getAmount(), payNow);
        return mapToResponse(billing);
    }

    // ─── NEW: Dashboard Summary ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BillingSummaryResponse getDashboardSummary(Long tenantId) {
        long totalInvoices = billingRepository.countByTenantId(tenantId);
        long pendingCount = billingRepository.countByTenantIdAndPaymentStatus(tenantId, PaymentStatus.PENDING);
        long paidCount = billingRepository.countByTenantIdAndPaymentStatus(tenantId, PaymentStatus.PAID);
        long partialCount = billingRepository.countByTenantIdAndPaymentStatus(tenantId, PaymentStatus.PARTIAL);

        BigDecimal totalRevenue = billingRepository.sumNetAmountByTenantIdAndPaymentStatus(tenantId, PaymentStatus.PAID);
        BigDecimal pendingAmount = billingRepository.sumNetAmountByTenantIdAndPaymentStatus(tenantId, PaymentStatus.PENDING);

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);
        BigDecimal collectedToday = billingRepository.sumPaidAmountToday(tenantId, startOfDay, endOfDay);

        return BillingSummaryResponse.builder()
                .totalInvoices(totalInvoices)
                .pendingCount(pendingCount)
                .paidCount(paidCount)
                .partialCount(partialCount)
                .totalRevenue(totalRevenue)
                .pendingAmount(pendingAmount)
                .collectedToday(collectedToday)
                .build();
    }

    // ─── Phase 3: Calculate Insurance Split ─────────────────────────────────

    /**
     * Called by billing controller when TPA has approved a pre-auth.
     * Calculates exact patient liability from policy limits.
     */
    @Transactional
    public BillingResponse calculateInsuranceSplit(Long billingId, Long policyId, Long tenantId) {
        Billing billing = billingRepository.findByIdAndTenantId(billingId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Billing", "id", billingId));

        InsurancePolicy policy = insurancePolicyRepository.findByIdAndTenantId(policyId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("InsurancePolicy", "id", policyId));

        BigDecimal gross = billing.getTotalAmount();
        BigDecimal nonPayable = BigDecimal.ZERO;
        BigDecimal roomRentDeductible = BigDecimal.ZERO;

        // Step 1: Separate non-payable items
        for (BillingItem item : billing.getItems()) {
            boolean itemPayable = item.getInsuranceCoverage() != com.curamatrix.hsm.enums.InsuranceCoverage.NOT_COVERED;
            if (!itemPayable) {
                nonPayable = nonPayable.add(item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }

        // Step 2: Room rent proportionality — future enhancement can pull actual room rent from admission
        // For now, if roomRentLimit is set and > 0, we apply a placeholder calculation
        // (Full implementation needs actual room rent from bed/ward)
        // roomRentDeductible remains 0 for OPD; IPD will enhance this in Phase 4

        // Step 3: Covered base
        BigDecimal coveredBase = gross.subtract(nonPayable).subtract(roomRentDeductible);
        if (coveredBase.compareTo(BigDecimal.ZERO) < 0) coveredBase = BigDecimal.ZERO;

        // Step 4: Co-pay
        BigDecimal copayPct = policy.getCopayPct() != null ? policy.getCopayPct() : BigDecimal.ZERO;
        BigDecimal copayAmount = coveredBase.multiply(copayPct).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);

        // Step 5: Patient liability and insurance claim
        BigDecimal patientLiability = nonPayable.add(roomRentDeductible).add(copayAmount);
        if (patientLiability.compareTo(BigDecimal.ZERO) < 0) patientLiability = BigDecimal.ZERO;
        BigDecimal insuranceClaim = gross.subtract(patientLiability);
        if (insuranceClaim.compareTo(BigDecimal.ZERO) < 0) insuranceClaim = BigDecimal.ZERO;

        // Persist split
        BillInsuranceSplit split = splitRepository.findByBillingId(billingId)
                .orElse(BillInsuranceSplit.builder().billing(billing).build());
        split.setInsurancePolicy(policy);
        split.setGrossAmount(gross);
        split.setNonPayableAmount(nonPayable);
        split.setRoomRentDeductible(roomRentDeductible);
        split.setCoveredBase(coveredBase);
        split.setCopayAmount(copayAmount);
        split.setPatientLiability(patientLiability);
        split.setInsuranceClaim(insuranceClaim);
        split.setTenantId(tenantId);
        splitRepository.save(split);

        // Update billing net amount to patient liability
        billing.setInsuranceAdjustment(insuranceClaim);
        billing.setNetAmount(patientLiability);
        billingRepository.save(billing);

        log.info("Insurance split calculated for billing {}: patient=₹{}, insurance=₹{}", billingId, patientLiability, insuranceClaim);
        return mapToResponse(billing);
    }

    // ─── Legacy markAsPaid (preserved for backward compat) ───────────────────

    @Transactional
    public void markAsPaid(Long billingId, Long tenantId) {
        Billing billing = billingRepository.findById(billingId)
                .orElseThrow(() -> new RuntimeException("Billing not found"));

        if (billing.getPaymentStatus() != PaymentStatus.PAID) {
            billing.setPaymentStatus(PaymentStatus.PAID);
            billing.setPaidAmount(billing.getNetAmount());
            billing.setPaidAt(LocalDateTime.now());
            
            billing.getItems().forEach(item -> {
                item.setPaymentStatus(PaymentStatus.PAID);
                item.setPaidAmount(item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity())));
            });
            
            billingRepository.save(billing);

            boolean hasRegistration = billing.getItems().stream()
                    .anyMatch(i -> i.getItemType() == BillingItemType.REGISTRATION);
            if (hasRegistration) {
                issueNewCasePaper(billing.getPatient(), billing, tenantId);
            }
        }
    }

    // ─── Helper: Recalculate Net Amount ──────────────────────────────────────

    private void recalculateNetAmount(Billing billing) {
        BigDecimal net = billing.getTotalAmount()
                .subtract(billing.getDiscount())
                .subtract(billing.getInsuranceAdjustment() != null ? billing.getInsuranceAdjustment() : BigDecimal.ZERO)
                .add(billing.getTax());
        if (net.compareTo(BigDecimal.ZERO) < 0) {
            net = BigDecimal.ZERO;
        }
        billing.setNetAmount(net);
    }

    // ─── DTO Mapping ─────────────────────────────────────────────────────────

    public BillingResponse mapToResponse(Billing billing) {
        BigDecimal balance = billing.getNetAmount().subtract(billing.getPaidAmount());
        if (balance.compareTo(BigDecimal.ZERO) < 0) balance = BigDecimal.ZERO;

        List<BillingItemResponse> itemResponses = billing.getItems().stream()
                .map(this::mapItemToResponse)
                .collect(Collectors.toList());

        Patient patient = billing.getPatient();

        BillingResponse resp = BillingResponse.builder()
                .id(billing.getId())
                .invoiceNumber(billing.getInvoiceNumber())
                .patientId(patient.getId())
                .patientName(patient.getFirstName() + " " + patient.getLastName())
                .patientCode(patient.getPatientCode())
                .appointmentId(billing.getAppointment() != null ? billing.getAppointment().getId() : null)
                .totalAmount(billing.getTotalAmount())
                .discount(billing.getDiscount())
                .tax(billing.getTax())
                .insuranceAdjustment(billing.getInsuranceAdjustment())
                .netAmount(billing.getNetAmount())
                .paidAmount(billing.getPaidAmount())
                .balanceAmount(balance)
                .paymentStatus(billing.getPaymentStatus().name())
                .paymentMethod(billing.getPaymentMethod() != null ? billing.getPaymentMethod().name() : null)
                .items(itemResponses)
                .createdAt(billing.getCreatedAt())
                .paidAt(billing.getPaidAt())
                .createdByName(billing.getCreatedBy() != null ? billing.getCreatedBy().getFullName() : null)
                .remarks(billing.getRemarks())
                .build();

        // Attach insurance split if present
        splitRepository.findByBillingId(billing.getId()).ifPresent(split -> {
            resp.setInsuranceSplit(InsuranceSplitResponse.builder()
                    .splitId(split.getId())
                    .insurancePolicyId(split.getInsurancePolicy() != null ? split.getInsurancePolicy().getId() : null)
                    .insurerName(split.getInsurancePolicy() != null && split.getInsurancePolicy().getPayer() != null ? split.getInsurancePolicy().getPayer().getInsurerName() : null)
                    .policyNumber(split.getInsurancePolicy() != null ? split.getInsurancePolicy().getPolicyNumber() : null)
                    .grossAmount(split.getGrossAmount())
                    .nonPayableAmount(split.getNonPayableAmount())
                    .roomRentDeductible(split.getRoomRentDeductible())
                    .coveredBase(split.getCoveredBase())
                    .copayAmount(split.getCopayAmount())
                    .patientLiability(split.getPatientLiability())
                    .insuranceClaim(split.getInsuranceClaim())
                    .calculatedAt(split.getCalculatedAt())
                    .build());
        });

        return resp;
    }

    private BillingItemResponse mapItemToResponse(BillingItem item) {
        BigDecimal subtotal = item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity()));
        return BillingItemResponse.builder()
                .id(item.getId())
                .description(item.getDescription())
                .amount(item.getAmount())
                .quantity(item.getQuantity())
                .itemType(item.getItemType().name())
                .insuranceCoverage(item.getInsuranceCoverage() != null ? item.getInsuranceCoverage().name() : null)
                .subtotal(subtotal)
                .paymentStatus(item.getPaymentStatus() != null ? item.getPaymentStatus().name() : "PENDING")
                .paidAmount(item.getPaidAmount())
                .departmentId(item.getDepartment() != null ? item.getDepartment().getId() : null)
                .departmentName(item.getDepartment() != null ? item.getDepartment().getName() : null)
                .serviceCatalogItemId(item.getHospitalService() != null ? item.getHospitalService().getId() : null)
                .createdAt(item.getCreatedAt())
                .build();
    }
}
