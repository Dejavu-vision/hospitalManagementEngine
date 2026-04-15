package com.curamatrix.hsm.service;

import com.curamatrix.hsm.dto.request.AddBillingItemRequest;
import com.curamatrix.hsm.dto.request.CollectPaymentRequest;
import com.curamatrix.hsm.dto.response.BillingItemResponse;
import com.curamatrix.hsm.dto.response.BillingResponse;
import com.curamatrix.hsm.dto.response.BillingSummaryResponse;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.BillingItemType;
import com.curamatrix.hsm.enums.PaymentMethod;
import com.curamatrix.hsm.enums.PaymentStatus;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.BillingRepository;
import com.curamatrix.hsm.repository.HospitalServiceRepository;
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
    private final HospitalServiceRepository hospitalServiceRepository;
    private final PatientRegistrationRepository patientRegistrationRepository;

    // ─── Registration Validation ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public boolean isRegistrationValid(Long patientId, Long tenantId) {
        Optional<PatientRegistration> latest = patientRegistrationRepository.findLatestActiveRegistration(patientId, tenantId);
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
                .paidAmount(payNow ? totalAmount : BigDecimal.ZERO)
                .paidAt(payNow ? LocalDateTime.now() : null)
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

    // ─── Registration Billing (existing flow — preserved) ────────────────────

    @Transactional
    public Billing createRegistrationBilling(Patient patient, Long tenantId, String paymentMethodStr) {
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
                .paidAmount(amount)
                .paidAt(LocalDateTime.now())
                .items(List.of(regItem))
                .build();

        billing.setTenantId(tenantId);
        regItem.setBilling(billing);

        Billing savedBilling = billingRepository.save(billing);
        issueNewCasePaper(patient, savedBilling, tenantId);

        return savedBilling;
    }

    private void issueNewCasePaper(Patient patient, Billing billing, Long tenantId) {
        int validityDays = 30;
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

            // If registration was pending, activate case paper
            boolean hasRegistration = billing.getItems().stream()
                    .anyMatch(i -> i.getItemType() == BillingItemType.REGISTRATION);
            if (hasRegistration) {
                issueNewCasePaper(billing.getPatient(), billing, tenantId);
            }
        } else {
            // Partial payment
            billing.setPaymentStatus(PaymentStatus.PARTIAL);
        }

        billing = billingRepository.save(billing);
        log.info("Payment collected for billing {}: amount={}, method={}, status={}",
                billingId, request.getAmount(), paymentMethod, billing.getPaymentStatus());

        return mapToResponse(billing);
    }

    // ─── NEW: Apply Discount ─────────────────────────────────────────────────

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

        BillingItem newItem = BillingItem.builder()
                .billing(billing)
                .description(request.getDescription())
                .amount(request.getAmount())
                .quantity(quantity)
                .itemType(itemType)
                .build();

        billing.getItems().add(newItem);
        billing.setTotalAmount(billing.getTotalAmount().add(request.getAmount().multiply(BigDecimal.valueOf(quantity))));
        recalculateNetAmount(billing);
        billing = billingRepository.save(billing);

        log.info("Item added to billing {}: desc={}, amount={}", billingId, request.getDescription(), request.getAmount());
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

    // ─── Legacy markAsPaid (preserved for backward compat) ───────────────────

    @Transactional
    public void markAsPaid(Long billingId, Long tenantId) {
        Billing billing = billingRepository.findById(billingId)
                .orElseThrow(() -> new RuntimeException("Billing not found"));

        if (billing.getPaymentStatus() != PaymentStatus.PAID) {
            billing.setPaymentStatus(PaymentStatus.PAID);
            billing.setPaidAmount(billing.getNetAmount());
            billing.setPaidAt(LocalDateTime.now());
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

        return BillingResponse.builder()
                .id(billing.getId())
                .invoiceNumber(billing.getInvoiceNumber())
                .patientId(patient.getId())
                .patientName(patient.getFirstName() + " " + patient.getLastName())
                .patientCode(patient.getPatientCode())
                .appointmentId(billing.getAppointment() != null ? billing.getAppointment().getId() : null)
                .totalAmount(billing.getTotalAmount())
                .discount(billing.getDiscount())
                .tax(billing.getTax())
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
    }

    private BillingItemResponse mapItemToResponse(BillingItem item) {
        BigDecimal subtotal = item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity()));
        return BillingItemResponse.builder()
                .id(item.getId())
                .description(item.getDescription())
                .amount(item.getAmount())
                .quantity(item.getQuantity())
                .itemType(item.getItemType().name())
                .subtotal(subtotal)
                .build();
    }
}
