package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.IpdChargeRequest;
import com.curamatrix.hsm.dto.request.IpdSettlementRequest;
import com.curamatrix.hsm.dto.request.SectionDiscountRequest;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.*;
import com.curamatrix.hsm.exception.InvalidStateTransitionException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.dto.request.RespondDiscountRequest;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Unified patient billing operations:
 * - Add / remove manual charges to the running bill
 * - Freeze bill (lock before discharge)
 * - Final settlement and discharge
 * - Running bill summary (enriched with TPA/insurance, doctor, ward/bed, stay-duration data)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IpdBillingService {

    private final IpdAdmissionRepository admissionRepository;
    private final BillingRepository billingRepository;
    private final BedAllocationRepository allocationRepository;
    private final BedRepository bedRepository;
    private final PaymentRepository paymentRepository;
    private final PreAuthRequestRepository preAuthRequestRepository;
    private final PatientRepository patientRepository;
    private final CatalogResolverService catalogResolver;
    private final QueueEventService queueEventService;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final DepartmentRepository departmentRepository;
    private final HospitalServiceRepository hospitalServiceRepository;
    private final BillingItemRepository billingItemRepository;


    // ── Unified Lookup Helpers ────────────────────────────────────────────────

    private IpdAdmission getActiveAdmission(Long patientId, Long tenantId) {
        IpdAdmission active = admissionRepository.findByStatusAndTenantId(AdmissionStatus.ADMITTED, tenantId).stream()
                .filter(a -> a.getPatient().getId().equals(patientId))
                .findFirst().orElse(null);
        if (active != null) {
            return active;
        }
        return admissionRepository.findByPatientIdAndTenantId(patientId, tenantId).stream()
                .filter(a -> a.getStatus() == AdmissionStatus.DISCHARGED)
                .max(java.util.Comparator.comparing(IpdAdmission::getAdmissionTime, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .orElse(null);
    }

    private List<Billing> getAllPendingBills(Long patientId, Long tenantId) {
        return billingRepository.findAllByPatientIdAndTenantId(patientId, tenantId).stream()
                .filter(b -> b.getPaymentStatus() == PaymentStatus.PENDING || b.getPaymentStatus() == PaymentStatus.PARTIAL)
                .collect(Collectors.toList());
    }

    private List<Billing> getRelevantBillsForSummary(Long patientId, Long tenantId, IpdAdmission admission) {
        List<Billing> allBills = billingRepository.findAllByPatientIdAndTenantId(patientId, tenantId);
        allBills.sort(Comparator.comparing(Billing::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return allBills;
    }

    private Billing getPrimaryBill(Long patientId, Long tenantId, IpdAdmission admission) {
        if (admission != null) {
            return billingRepository.findByIpdAdmissionId(admission.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Running Bill", "admissionId", admission.getId()));
        } else {
            List<Billing> bills = getAllPendingBills(patientId, tenantId);
            if (bills.isEmpty()) {
                Patient patient = patientRepository.findById(patientId)
                        .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId));
                Billing b = Billing.builder()
                        .patient(patient)
                        .invoiceNumber("OPD-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .totalAmount(BigDecimal.ZERO)
                        .netAmount(BigDecimal.ZERO)
                        .paidAmount(BigDecimal.ZERO)
                        .paymentStatus(PaymentStatus.PENDING)
                        .items(new ArrayList<>())
                        .build();
                b.setTenantId(tenantId);
                return billingRepository.save(b);
            }
            return bills.get(0); // Use the most recent pending bill as primary
        }
    }

    private Long getCurrentUserId() {
        try {
            org.springframework.security.core.Authentication auth = 
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                Optional<User> userOpt = userRepository.findByEmail(auth.getName());
                if (userOpt.isPresent()) {
                    return userOpt.get().getId();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve current user from context", e);
        }
        return null;
    }

    // ── Add a manual charge to the running bill ───────────────────────────────

    @Transactional
    public Map<String, Object> addCharge(Long patientId, IpdChargeRequest req) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = getActiveAdmission(patientId, tenantId);
        Billing bill = getPrimaryBill(patientId, tenantId, admission);


        BillingItemType itemType = mapChargeCategory(req.getChargeCategory());
        int qty = req.getQuantity() != null ? req.getQuantity() : 1;
        BigDecimal total = req.getUnitPrice().multiply(BigDecimal.valueOf(qty));

        Department dept = req.getDepartmentId() != null ? 
                departmentRepository.findById(req.getDepartmentId()).orElse(null) : null;
        HospitalService hs = req.getServiceCatalogItemId() != null ?
                hospitalServiceRepository.findById(req.getServiceCatalogItemId()).orElse(null) : null;

        BillingItem item = BillingItem.builder()
                .billing(bill)
                .description(req.getDescription())
                .amount(req.getUnitPrice())
                .quantity(qty)
                .itemType(itemType)
                .department(dept)
                .hospitalService(hs)
                .build();

        boolean payNow = Boolean.TRUE.equals(req.getPayNow());
        if (payNow) {
            item.setPaymentStatus(PaymentStatus.PAID);
            item.setPaidAmount(total);
            bill.setPaidAmount(bill.getPaidAmount().add(total));
            if (req.getPaymentMethod() != null) {
                try {
                    bill.setPaymentMethod(PaymentMethod.valueOf(req.getPaymentMethod().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    bill.setPaymentMethod(PaymentMethod.CASH);
                }
            }
        } else {
            item.setPaymentStatus(PaymentStatus.PENDING);
            item.setPaidAmount(BigDecimal.ZERO);
        }
        item = billingItemRepository.save(item);

        bill.getItems().add(item);
        bill.setTotalAmount(bill.getTotalAmount().add(total));
        recalcNet(bill);
        billingRepository.save(bill);

        if (payNow) {
            Long collectedById = getCurrentUserId();
            PaymentMethod pm = PaymentMethod.CASH;
            if (req.getPaymentMethod() != null) {
                try {
                    pm = PaymentMethod.valueOf(req.getPaymentMethod().toUpperCase());
                } catch (IllegalArgumentException e) {
                    pm = PaymentMethod.CASH;
                }
            }
            Payment payment = Payment.builder()
                    .patient(bill.getPatient())
                    .billing(bill)
                    .billingItem(item)
                    .amount(total)
                    .method(pm)
                    .collectedById(collectedById)
                    .build();
            payment.setTenantId(tenantId);
            paymentRepository.save(payment);
        }

        log.info("Charge added for patient {}: {} x{} = ₹{}", patientId, req.getDescription(), qty, total);

        PreAuthRequest preAuth = admission != null ? loadPreAuth(admission.getId(), tenantId) : null;
        BedAllocation currentAllocation = admission != null ? allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admission.getId(), tenantId).orElse(null) : null;
        return buildUnifiedRunningBillSummary(patientId, admission, getRelevantBillsForSummary(patientId, tenantId, admission), preAuth, currentAllocation);
    }

    @Transactional
    public Map<String, Object> settleChargeItem(Long patientId, Long itemId, String paymentMethodStr, BigDecimal amountToPay) {
        Long tenantId = TenantContext.getTenantId();
        IpdAdmission admission = getActiveAdmission(patientId, tenantId);
        
        BillingItem item = billingItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingItem", "id", itemId));
        Billing bill = item.getBilling();
        if (bill == null) {
            throw new ResourceNotFoundException("Billing", "itemId", itemId);
        }

        BigDecimal itemTotal = item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity()));
        BigDecimal itemPaid = item.getPaidAmount() != null ? item.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal remainingAmount = itemTotal.subtract(itemPaid);

        BigDecimal actualPayment = (amountToPay != null) ? amountToPay : remainingAmount;

        if (actualPayment.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        if (actualPayment.compareTo(remainingAmount) > 0) {
            throw new IllegalArgumentException("Payment amount cannot exceed the remaining due amount");
        }

        BigDecimal newPaidAmount = itemPaid.add(actualPayment);
        item.setPaidAmount(newPaidAmount);

        if (newPaidAmount.compareTo(itemTotal) >= 0) {
            item.setPaymentStatus(PaymentStatus.PAID);
        } else {
            item.setPaymentStatus(PaymentStatus.PARTIAL);
        }
        billingItemRepository.save(item);

        // Accumulate in the bill's paidAmount as well
        bill.setPaidAmount((bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO).add(actualPayment));
        PaymentMethod pm = PaymentMethod.CASH;
        if (paymentMethodStr != null) {
            try {
                pm = PaymentMethod.valueOf(paymentMethodStr.toUpperCase());
                bill.setPaymentMethod(pm);
            } catch (IllegalArgumentException e) {
                bill.setPaymentMethod(PaymentMethod.CASH);
            }
        }
        recalcNet(bill);
        billingRepository.save(bill);

        Long collectedById = getCurrentUserId();
        Payment payment = Payment.builder()
                .patient(bill.getPatient())
                .billing(bill)
                .billingItem(item)
                .amount(actualPayment)
                .method(pm)
                .collectedById(collectedById)
                .build();
        payment.setTenantId(tenantId);
        paymentRepository.save(payment);

        log.info("Billing item {} settled for patient {}: amount=₹{}, method={}", 
                itemId, patientId, actualPayment, paymentMethodStr);

        PreAuthRequest preAuth = admission != null ? loadPreAuth(admission.getId(), tenantId) : null;
        BedAllocation currentAllocation = admission != null ? allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admission.getId(), tenantId).orElse(null) : null;

        return buildUnifiedRunningBillSummary(patientId, admission, getRelevantBillsForSummary(patientId, tenantId, admission), preAuth, currentAllocation);
    }

    @Transactional
    public Map<String, Object> settleChargeItems(Long patientId, List<Long> itemIds, String paymentMethodStr) {
        Long tenantId = TenantContext.getTenantId();
        IpdAdmission admission = getActiveAdmission(patientId, tenantId);
        Long collectedById = getCurrentUserId();
        PaymentMethod pm = PaymentMethod.CASH;
        if (paymentMethodStr != null) {
            try {
                pm = PaymentMethod.valueOf(paymentMethodStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                pm = PaymentMethod.CASH;
            }
        }

        java.util.Set<Billing> modifiedBills = new java.util.HashSet<>();
        for (Long itemId : itemIds) {
            BillingItem item = billingItemRepository.findById(itemId).orElse(null);
            if (item != null && item.getPaymentStatus() != PaymentStatus.PAID) {
                Billing itemBill = item.getBilling();
                if (itemBill != null) {
                    BigDecimal itemTotal = item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity()));
                    item.setPaymentStatus(PaymentStatus.PAID);
                    item.setPaidAmount(itemTotal);
                    billingItemRepository.save(item);

                    itemBill.setPaidAmount(itemBill.getPaidAmount().add(itemTotal));
                    itemBill.setPaymentMethod(pm);
                    modifiedBills.add(itemBill);

                    Payment payment = Payment.builder()
                            .patient(itemBill.getPatient())
                            .billing(itemBill)
                            .billingItem(item)
                            .amount(itemTotal)
                            .method(pm)
                            .collectedById(collectedById)
                            .build();
                    payment.setTenantId(tenantId);
                    paymentRepository.save(payment);
                }
            }
        }

        for (Billing modifiedBill : modifiedBills) {
            recalcNet(modifiedBill);
            billingRepository.save(modifiedBill);
        }

        PreAuthRequest preAuth = admission != null ? loadPreAuth(admission.getId(), tenantId) : null;
        BedAllocation currentAllocation = admission != null ? allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admission.getId(), tenantId).orElse(null) : null;

        return buildUnifiedRunningBillSummary(patientId, admission, getRelevantBillsForSummary(patientId, tenantId, admission), preAuth, currentAllocation);
    }

    // ── Remove a manual charge (only before freeze) ───────────────────────────

    @Transactional
    public Map<String, Object> removeCharge(Long patientId, Long itemId) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = getActiveAdmission(patientId, tenantId);
        List<Billing> allBills = billingRepository.findAllByPatientIdAndTenantId(patientId, tenantId);

        // Find the bill containing this item
        Billing bill = allBills.stream()
                .filter(b -> b.getItems().stream().anyMatch(i -> i.getId().equals(itemId)))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("BillingItem", "id", itemId));

        BillingItem toRemove = bill.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("BillingItem", "id", itemId));

        if (toRemove.getItemType() == BillingItemType.BED_CHARGE ||
            toRemove.getItemType() == BillingItemType.NURSING_CHARGE) {
            throw new IllegalArgumentException("Auto-generated bed/nursing charges cannot be removed manually.");
        }

        BigDecimal itemTotal = toRemove.getAmount().multiply(BigDecimal.valueOf(toRemove.getQuantity()));
        bill.getItems().remove(toRemove);
        bill.setTotalAmount(bill.getTotalAmount().subtract(itemTotal));
        recalcNet(bill);
        billingRepository.save(bill);

        log.info("Charge {} removed for patient {}", itemId, patientId);

        PreAuthRequest preAuth = admission != null ? loadPreAuth(admission.getId(), tenantId) : null;
        BedAllocation currentAllocation = admission != null ? allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admission.getId(), tenantId).orElse(null) : null;
        return buildUnifiedRunningBillSummary(patientId, admission, getRelevantBillsForSummary(patientId, tenantId, admission), preAuth, currentAllocation);
    }

    // ── Retroactively change a bed charge's bed and rate ──────────────────────

    @Transactional
    public Map<String, Object> changeBedForChargeRow(Long patientId, Long itemId, Long newBedId) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = getActiveAdmission(patientId, tenantId);
        List<Billing> allBills = billingRepository.findAllByPatientIdAndTenantId(patientId, tenantId);

        // Find the bill containing this item
        Billing bill = allBills.stream()
                .filter(b -> b.getItems().stream().anyMatch(i -> i.getId().equals(itemId)))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("BillingItem", "id", itemId));

        if (bill.getRemarks() != null && bill.getRemarks().startsWith("FROZEN")) {
            throw new InvalidStateTransitionException("Billing", "FROZEN", "CHANGE_BED_CHARGE");
        }

        BillingItem toUpdate = bill.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("BillingItem", "id", itemId));

        if (toUpdate.getItemType() != BillingItemType.BED_CHARGE) {
            throw new IllegalArgumentException("Only bed charge rows can have their bed edited.");
        }

        Bed newBed = bedRepository.findById(newBedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed", "id", newBedId));

        if (!newBed.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Bed does not belong to this tenant.");
        }

        // Resolve new price from Service Catalog
        BigDecimal oldItemTotal = toUpdate.getAmount().multiply(BigDecimal.valueOf(toUpdate.getQuantity()));
        BigDecimal newUnitPrice = catalogResolver.resolveBedCharge(newBed.getRoom().getRoomType(), tenantId).getPrice();
        BigDecimal newItemTotal = newUnitPrice.multiply(BigDecimal.valueOf(toUpdate.getQuantity()));

        // Parse date from old description if present
        String originalDesc = toUpdate.getDescription();
        String dateStr = "";
        int onIdx = originalDesc.lastIndexOf(" on ");
        if (onIdx != -1) {
            dateStr = originalDesc.substring(onIdx);
        } else {
            dateStr = " on " + LocalDate.now();
        }

        toUpdate.setDescription("Daily Bed Charge (" + newBed.getBedNumber() + ")" + dateStr);
        toUpdate.setAmount(newUnitPrice);

        bill.setTotalAmount(bill.getTotalAmount().subtract(oldItemTotal).add(newItemTotal));
        recalcNet(bill);
        billingRepository.save(bill);

        log.info("Bed charge item {} updated for patient {}: new Bed {}, rate ₹{}", itemId, patientId, newBed.getBedNumber(), newUnitPrice);

        PreAuthRequest preAuth = admission != null ? loadPreAuth(admission.getId(), tenantId) : null;
        BedAllocation currentAllocation = admission != null ? allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admission.getId(), tenantId).orElse(null) : null;
        return buildUnifiedRunningBillSummary(patientId, admission, getRelevantBillsForSummary(patientId, tenantId, admission), preAuth, currentAllocation);
    }

    // ── Get running bill summary (enriched) ──────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getRunningBill(Long patientId) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = getActiveAdmission(patientId, tenantId);
        List<Billing> summaryBills = getRelevantBillsForSummary(patientId, tenantId, admission);

        if (admission == null && summaryBills.isEmpty()) {
            // Patient has no active bills and is not admitted. We'll return an empty structure.
            Patient p = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId));
            return buildUnifiedRunningBillSummary(patientId, null, Collections.emptyList(), null, null);
        }

        PreAuthRequest preAuth = admission != null ? loadPreAuth(admission.getId(), tenantId) : null;
        BedAllocation currentAllocation = admission != null ? allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admission.getId(), tenantId).orElse(null) : null;

        return buildUnifiedRunningBillSummary(patientId, admission, summaryBills, preAuth, currentAllocation);
    }

    // ── Clear discharge (Doctor marks clinical work done) ─────────────────────

    @Transactional
    public Map<String, Object> clearDischarge(Long patientId) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = getActiveAdmission(patientId, tenantId);
        if (admission == null) {
            throw new IllegalArgumentException("Patient is not admitted. Cannot clear discharge.");
        }

        if (admission.getStatus() == AdmissionStatus.DISCHARGED) {
            throw new InvalidStateTransitionException("Admission", "DISCHARGED", "CLEAR_DISCHARGE");
        }

        admission.setDischargeCleared(true);
        admissionRepository.save(admission);
        log.info("Discharge cleared for patient {}", patientId);

        PreAuthRequest preAuth = loadPreAuth(admission.getId(), tenantId);
        BedAllocation currentAllocation = allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admission.getId(), tenantId).orElse(null);
        return buildUnifiedRunningBillSummary(patientId, admission, getRelevantBillsForSummary(patientId, tenantId, admission), preAuth, currentAllocation);
    }

    // ── Generate Invoice (freeze + mark invoice generated) ───────────────────

    @Transactional
    public Map<String, Object> generateInvoice(Long patientId) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = getActiveAdmission(patientId, tenantId);
        List<Billing> pendingBills = getAllPendingBills(patientId, tenantId);

        if (admission != null) {
            if (admission.getStatus() != AdmissionStatus.ADMITTED && admission.getStatus() != AdmissionStatus.DISCHARGED) {
                throw new InvalidStateTransitionException("Admission", admission.getStatus().name(), "GENERATE_INVOICE");
            }
            if (!admission.isDischargeCleared()) {
                org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                boolean isBypassRole = auth != null && auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_RECEPTIONIST"));
                if (!isBypassRole) {
                    throw new InvalidStateTransitionException("Admission", "DISCHARGE_NOT_CLEARED", "GENERATE_INVOICE");
                }
            }
            admission.setInvoiceGenerated(true);
            admissionRepository.save(admission);
        }

        for (Billing bill : pendingBills) {
            if (bill.getRemarks() == null || !bill.getRemarks().startsWith("FROZEN")) {
                bill.setRemarks("FROZEN - " + LocalDateTime.now());
                billingRepository.save(bill);
            }
        }

        log.info("Invoice generated for patient {}", patientId);

        PreAuthRequest preAuth = admission != null ? loadPreAuth(admission.getId(), tenantId) : null;
        BedAllocation currentAllocation = admission != null ? allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admission.getId(), tenantId).orElse(null) : null;
        return buildUnifiedRunningBillSummary(patientId, admission, getRelevantBillsForSummary(patientId, tenantId, admission), preAuth, currentAllocation);
    }

    // ── Freeze bill (lock before discharge) ──────────────────────────────────

    @Transactional
    public Map<String, Object> freezeBill(Long patientId) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = getActiveAdmission(patientId, tenantId);
        List<Billing> pendingBills = getAllPendingBills(patientId, tenantId);

        if (admission != null && admission.getStatus() != AdmissionStatus.ADMITTED) {
            throw new InvalidStateTransitionException("Admission", admission.getStatus().name(), "FREEZE");
        }

        for (Billing bill : pendingBills) {
            bill.setRemarks("FROZEN - " + LocalDateTime.now());
            billingRepository.save(bill);
        }

        log.info("Bill frozen for patient {}", patientId);

        PreAuthRequest preAuth = admission != null ? loadPreAuth(admission.getId(), tenantId) : null;
        BedAllocation currentAllocation = admission != null ? allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admission.getId(), tenantId).orElse(null) : null;
        return buildUnifiedRunningBillSummary(patientId, admission, getRelevantBillsForSummary(patientId, tenantId, admission), preAuth, currentAllocation);
    }

    // ── Unfreeze bill (unlock) ────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> unfreezeBill(Long patientId) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = getActiveAdmission(patientId, tenantId);
        List<Billing> relevantBills = getRelevantBillsForSummary(patientId, tenantId, admission);

        if (admission != null && admission.getStatus() != AdmissionStatus.ADMITTED && admission.getStatus() != AdmissionStatus.DISCHARGED) {
            throw new InvalidStateTransitionException("Admission", admission.getStatus().name(), "UNFREEZE");
        }

        for (Billing bill : relevantBills) {
            if (bill.getRemarks() != null && bill.getRemarks().startsWith("FROZEN")) {
                // Remove the FROZEN prefix. If it's just FROZEN - date, set to null
                bill.setRemarks(null);
                billingRepository.save(bill);
            }
        }
        
        if (admission != null && admission.isInvoiceGenerated()) {
            admission.setInvoiceGenerated(false);
            admissionRepository.save(admission);
        }

        log.info("Bill unfrozen for patient {}", patientId);

        PreAuthRequest preAuth = admission != null ? loadPreAuth(admission.getId(), tenantId) : null;
        BedAllocation currentAllocation = admission != null ? allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admission.getId(), tenantId).orElse(null) : null;
        return buildUnifiedRunningBillSummary(patientId, admission, getRelevantBillsForSummary(patientId, tenantId, admission), preAuth, currentAllocation);
    }

    // ── Final settlement and discharge ────────────────────────────────────────

    @Transactional
    public Map<String, Object> finalSettlement(Long patientId, IpdSettlementRequest req) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = getActiveAdmission(patientId, tenantId);
        List<Billing> pendingBills = getAllPendingBills(patientId, tenantId);

        // Block settlement if receptionist is trying to settle and discount is not approved
        Billing primaryBillForCheck = getPrimaryBill(patientId, tenantId, admission);
        if (primaryBillForCheck != null && primaryBillForCheck.getDiscount().compareTo(BigDecimal.ZERO) > 0) {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            boolean isAdmin = auth != null && auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            
            boolean requireApproval = true;
            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
            if (tenant != null && tenant.getSettings() != null) {
                Object val = tenant.getSettings().get("requireDiscountApproval");
                if (val instanceof Boolean) {
                    requireApproval = (Boolean) val;
                } else if (val instanceof String) {
                    requireApproval = Boolean.parseBoolean((String) val);
                }
            }

            if (!isAdmin && Boolean.FALSE.equals(primaryBillForCheck.getDiscountApproved()) && requireApproval) {
                throw new IllegalStateException("Discount requires admin approval before settlement.");
            }
        }


        if (admission != null) {
            if (!admission.isInvoiceGenerated()) {
                throw new InvalidStateTransitionException("Admission", "INVOICE_NOT_GENERATED", "SETTLE");
            }
        }

        BigDecimal tpaApprovedAmount = BigDecimal.ZERO;
        PreAuthRequest preAuth = admission != null ? loadPreAuth(admission.getId(), tenantId) : null;
        if (preAuth != null && "APPROVED".equals(preAuth.getStatus().name())) {
            tpaApprovedAmount = preAuth.getApprovedAmount() != null ? preAuth.getApprovedAmount() : BigDecimal.ZERO;
        }

        BigDecimal remainingTpa = tpaApprovedAmount;

        // Apply TPA to bills
        for (Billing bill : pendingBills) {
            if (remainingTpa.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal billDue = bill.getNetAmount().subtract(bill.getPaidAmount());
                BigDecimal tpaToApply = remainingTpa.min(billDue);
                bill.setInsuranceAdjustment(tpaToApply);
                remainingTpa = remainingTpa.subtract(tpaToApply);
                recalcNet(bill);
            }
        }

        // Apply payment to the primary bill
        Billing primaryBill = getPrimaryBill(patientId, tenantId, admission);
        if (req.getAmount() != null && req.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            primaryBill.setPaidAmount(primaryBill.getPaidAmount().add(req.getAmount()));
        }
        if (req.getRefundAmount() != null && req.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
            primaryBill.setPaidAmount(primaryBill.getPaidAmount().subtract(req.getRefundAmount()));
        }

        Long collectedById = getCurrentUserId();
        PaymentMethod pm = PaymentMethod.CASH;
        if (req.getPaymentMethod() != null) {
            try {
                pm = PaymentMethod.valueOf(req.getPaymentMethod().toUpperCase());
            } catch (IllegalArgumentException e) {
                pm = PaymentMethod.CASH;
            }
        }

        if (req.getAmount() != null && req.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            Payment payment = Payment.builder()
                    .patient(primaryBill.getPatient())
                    .billing(primaryBill)
                    .amount(req.getAmount())
                    .method(pm)
                    .referenceNumber(req.getTransactionRef())
                    .collectedById(collectedById)
                    .build();
            payment.setTenantId(tenantId);
            paymentRepository.save(payment);
        }

        if (req.getRefundAmount() != null && req.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
            Payment refund = Payment.builder()
                    .patient(primaryBill.getPatient())
                    .billing(primaryBill)
                    .amount(req.getRefundAmount().negate())
                    .method(pm)
                    .referenceNumber(req.getTransactionRef())
                    .collectedById(collectedById)
                    .build();
            refund.setTenantId(tenantId);
            paymentRepository.save(refund);
        }

        BigDecimal totalCharges = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        
        // Finalize all bills
        for (Billing bill : pendingBills) {
            for (BillingItem item : bill.getItems()) {
                if (item.getPaymentStatus() != PaymentStatus.PAID) {
                    BigDecimal itemTotal = item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity()));
                    item.setPaymentStatus(PaymentStatus.PAID);
                    item.setPaidAmount(itemTotal);
                    billingItemRepository.save(item);
                }
            }
            bill.setPaymentStatus(PaymentStatus.PAID);
            bill.setPaidAt(LocalDateTime.now());
            bill.setPaymentMethod(pm);
            billingRepository.save(bill);
            totalCharges = totalCharges.add(bill.getTotalAmount());
            totalPaid = totalPaid.add(bill.getPaidAmount());
        }

        String bedNumber = null;
        if (admission != null) {
            BedAllocation alloc = allocationRepository
                    .findByAdmissionIdAndIsCurrentTrueAndTenantId(admission.getId(), tenantId).orElse(null);
            if (alloc != null) {
                alloc.setIsCurrent(false);
                alloc.setEndTime(LocalDateTime.now());
                allocationRepository.save(alloc);
                Bed bed = alloc.getBed();
                bedNumber = bed.getBedNumber();
                bed.setStatus(BedStatus.CLEANING);
                bedRepository.save(bed);
            }

            if (admission.getStatus() != AdmissionStatus.DISCHARGED) {
                admission.setStatus(AdmissionStatus.DISCHARGED);
                admission.setActualDischargeTime(LocalDateTime.now());
                admissionRepository.save(admission);
            }
            log.info("IPD final settlement complete for patient {}. Bed {} released.", patientId, bedNumber);
        } else {
            log.info("OPD final settlement complete for patient {}.", patientId);
        }

        BigDecimal depositPaid = admission != null && admission.getDepositAmount() != null ? admission.getDepositAmount() : BigDecimal.ZERO;
        BigDecimal balanceCollected = req.getAmount() != null && req.getAmount().compareTo(BigDecimal.ZERO) > 0 ? req.getAmount() : BigDecimal.ZERO;
        BigDecimal refundAmount = req.getRefundAmount() != null ? req.getRefundAmount() : BigDecimal.ZERO;

        Patient patient = patientRepository.findById(patientId).orElseThrow();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("patientId", patientId);
        result.put("admissionId", admission != null ? admission.getId() : null);
        result.put("admissionNumber", admission != null ? admission.getAdmissionNumber() : "OPD");
        result.put("patientName", patient.getFirstName() + " " + patient.getLastName());
        result.put("invoiceNumber", primaryBill.getInvoiceNumber());
        result.put("status", admission != null ? "DISCHARGED" : "SETTLED");
        result.put("totalCharges", totalCharges);
        result.put("depositPaid", depositPaid);
        result.put("tpaApprovedAmount", tpaApprovedAmount);
        result.put("balanceCollected", balanceCollected);
        result.put("refundAmount", refundAmount);
        result.put("totalPaid", totalPaid.add(depositPaid));
        result.put("bedReleased", bedNumber);
        result.put("dischargeTime", admission != null && admission.getActualDischargeTime() != null ? admission.getActualDischargeTime().toString() : LocalDateTime.now().toString());
        return result;
    }

    // ── Final bill breakdown ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getFinalBill(Long patientId) {
        Long tenantId = TenantContext.getTenantId();
        
        IpdAdmission admission = getActiveAdmission(patientId, tenantId);
        List<Billing> bills = getRelevantBillsForSummary(patientId, tenantId, admission);
        if (bills.isEmpty() && admission != null) {
            billingRepository.findByIpdAdmissionId(admission.getId()).ifPresent(bills::add);
        }

        BigDecimal tpaApprovedAmount = BigDecimal.ZERO;
        if (admission != null && admission.getPreAuthId() != null) {
            tpaApprovedAmount = bills.stream()
                .map(b -> b.getInsuranceAdjustment() != null ? b.getInsuranceAdjustment() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal totalCharges = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        
        for (Billing bill : bills) {
            totalCharges = totalCharges.add(bill.getTotalAmount());
            totalPaid = totalPaid.add(bill.getPaidAmount());
            totalDiscount = totalDiscount.add(bill.getDiscount() != null ? bill.getDiscount() : BigDecimal.ZERO);
            for (BillingItem item : bill.getItems()) {
                String cat = item.getItemType().name();
                BigDecimal subtotal = item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity()));
                byCategory.merge(cat, subtotal, BigDecimal::add);
            }
        }

        BigDecimal depositPaid = admission != null && admission.getDepositAmount() != null ? admission.getDepositAmount() : BigDecimal.ZERO;
        BigDecimal copayCollected = totalPaid;

        boolean allPaid = !bills.isEmpty() && bills.stream().allMatch(b -> b.getPaymentStatus() == PaymentStatus.PAID);

        BigDecimal balanceDue = BigDecimal.ZERO;
        if (!allPaid) {
            balanceDue = totalCharges
                    .subtract(totalDiscount)
                    .subtract(depositPaid)
                    .subtract(tpaApprovedAmount)
                    .subtract(copayCollected);
        }

        Patient patient = patientRepository.findById(patientId).orElseThrow();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("patientId", patientId);
        result.put("admissionId", admission != null ? admission.getId() : null);
        result.put("admissionNumber", admission != null ? admission.getAdmissionNumber() : "OPD");
        result.put("patientName", patient.getFirstName() + " " + patient.getLastName());
        result.put("patientCode", patient.getPatientCode());
        result.put("admissionTime", admission != null && admission.getAdmissionTime() != null ? admission.getAdmissionTime().toString() : null);
        result.put("dischargeTime", admission != null && admission.getActualDischargeTime() != null ? admission.getActualDischargeTime().toString() : null);
        result.put("chargesByCategory", byCategory);
        result.put("totalCharges", totalCharges);
        result.put("totalDiscount", totalDiscount);
        result.put("depositPaid", depositPaid);
        result.put("tpaApprovedAmount", tpaApprovedAmount);
        result.put("copayCollected", copayCollected);
        result.put("balanceDue", balanceDue);
        
        boolean isFrozen = bills.stream().anyMatch(b -> b.getRemarks() != null && b.getRemarks().startsWith("FROZEN"));
        result.put("isFrozen", isFrozen);
        result.put("paymentStatus", allPaid ? "PAID" : "PENDING");
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PreAuthRequest loadPreAuth(Long admissionId, Long tenantId) {
        IpdAdmission admission = admissionRepository.findById(admissionId).orElse(null);
        if (admission != null && admission.getPreAuthId() != null) {
            PreAuthRequest pa = preAuthRequestRepository.findById(admission.getPreAuthId()).orElse(null);
            if (pa != null) return pa;
        }
        List<PreAuthRequest> byAdmission = preAuthRequestRepository
                .findByAdmissionIdAndTenantId(admissionId, tenantId);
        if (!byAdmission.isEmpty()) return byAdmission.get(0);
        
        if (admission != null) {
            List<PreAuthRequest> byPatient = preAuthRequestRepository
                    .findByPatientIdAndTenantIdOrderByRequestedAtDesc(admission.getPatient().getId(), tenantId);
            if (!byPatient.isEmpty()) {
                PreAuthRequest pa = byPatient.get(0);
                if (pa.getAdmissionId() == null) {
                    pa.setAdmissionId(admissionId);
                    preAuthRequestRepository.save(pa);
                    admission.setPreAuthId(pa.getId());
                    admissionRepository.save(admission);
                }
                return pa;
            }
        }
        return null;
    }

    private Map<String, Object> buildUnifiedRunningBillSummary(
            Long patientId,
            IpdAdmission admission,
            List<Billing> pendingBills,
            PreAuthRequest preAuth,
            BedAllocation currentAllocation
    ) {
        Long tenantId = TenantContext.getTenantId();
        Patient patient = patientRepository.findById(patientId).orElseThrow();
        
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal totalCharges = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        boolean isFrozen = false;

        // Define the 6 sections in order
        List<String> sections = List.of("ROOM_NURSING", "PROCEDURE", "DOCTOR", "LAB", "MEDICINE", "OTHER");
        Map<String, BigDecimal> grossMap = new LinkedHashMap<>();
        Map<String, BigDecimal> discMap = new LinkedHashMap<>();
        
        for (String s : sections) {
            grossMap.put(s, BigDecimal.ZERO);
            discMap.put(s, BigDecimal.ZERO);
        }
        
        for (Billing bill : pendingBills) {
            totalCharges = totalCharges.add(bill.getTotalAmount());
            totalPaid = totalPaid.add(bill.getPaidAmount());
            totalDiscount = totalDiscount.add(bill.getDiscount() != null ? bill.getDiscount() : BigDecimal.ZERO);
            if (bill.getRemarks() != null && bill.getRemarks().startsWith("FROZEN")) {
                isFrozen = true;
            }
            for (BillingItem item : bill.getItems()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", item.getId());
                m.put("description", item.getDescription());
                m.put("chargeCategory", item.getItemType().name());
                m.put("unitPrice", item.getAmount());
                m.put("quantity", item.getQuantity());
                m.put("totalAmount", item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity())));
                m.put("isAutoCharge", item.getItemType() == BillingItemType.BED_CHARGE ||
                                       item.getItemType() == BillingItemType.NURSING_CHARGE);
                m.put("paymentStatus", item.getPaymentStatus() != null ? item.getPaymentStatus().name() : "PENDING");
                m.put("paidAmount", item.getPaidAmount() != null ? item.getPaidAmount() : BigDecimal.ZERO);
                items.add(m);

                // Section breakdown grouping
                String secKey = getSectionKey(item.getItemType());
                BigDecimal itemTotal = item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity()));
                grossMap.put(secKey, grossMap.get(secKey).add(itemTotal));
            }

            // Section-wise discount sum
            Map<String, BigDecimal> billSecDiscs = bill.getSectionDiscountsMap();
            for (Map.Entry<String, BigDecimal> entry : billSecDiscs.entrySet()) {
                String secKey = entry.getKey();
                if (grossMap.containsKey(secKey)) {
                    discMap.put(secKey, discMap.get(secKey).add(entry.getValue()));
                }
            }
        }

        List<Map<String, Object>> sectionBreakdowns = new ArrayList<>();
        for (String s : sections) {
            Map<String, Object> breakdown = new LinkedHashMap<>();
            BigDecimal gross = grossMap.get(s);
            BigDecimal discount = discMap.get(s);
            if (discount.compareTo(gross) > 0) {
                discount = gross;
            }
            BigDecimal net = gross.subtract(discount);

            breakdown.put("key", s);
            breakdown.put("label", getSectionLabel(s));
            breakdown.put("grossAmount", gross);
            breakdown.put("discount", discount);
            breakdown.put("netAmount", net);
            sectionBreakdowns.add(breakdown);
        }

        BigDecimal depositPaid = admission != null && admission.getDepositAmount() != null ? admission.getDepositAmount() : BigDecimal.ZERO;
        
        BigDecimal tpaApprovedAmount = BigDecimal.ZERO;
        if (preAuth != null && "APPROVED".equals(preAuth.getStatus().name())) {
            tpaApprovedAmount = preAuth.getApprovedAmount() != null ? preAuth.getApprovedAmount() : BigDecimal.ZERO;
        }
        
        BigDecimal copayCollected = totalPaid;

        BigDecimal provisionalBalance = totalCharges
                .subtract(totalDiscount)
                .subtract(depositPaid)
                .subtract(copayCollected)
                .subtract(tpaApprovedAmount);

        int stayDays = 0;
        if (admission != null && admission.getAdmissionTime() != null) {
            stayDays = (int) ChronoUnit.DAYS.between(admission.getAdmissionTime().toLocalDate(), LocalDate.now()) + 1;
            if (stayDays < 1) stayDays = 1;
        }

        String primaryDoctorName = null;
        String primaryDoctorSpecialty = null;
        if (admission != null && admission.getPrimaryDoctor() != null) {
            primaryDoctorName = admission.getPrimaryDoctor().getUser().getFullName();
            if (admission.getPrimaryDoctor().getDepartment() != null) {
                primaryDoctorSpecialty = admission.getPrimaryDoctor().getDepartment().getName();
            }
        } else {
            // OPD Doctor fallback
            primaryDoctorName = "OPD Consultant";
        }

        String currentWardName = null;
        String currentBedNumber = null;
        if (currentAllocation != null) {
            Bed bed = currentAllocation.getBed();
            currentBedNumber = bed.getBedNumber();
            if (bed.getRoom() != null && bed.getRoom().getWard() != null) {
                currentWardName = bed.getRoom().getWard().getName();
            }
        } else if (admission == null) {
            currentWardName = "Outpatient";
            currentBedNumber = "OPD";
        }

        // Pre-auth Map
        Map<String, Object> preAuthMap = null;
        if (preAuth != null) {
            preAuthMap = new HashMap<>();
            preAuthMap.put("id", preAuth.getId());
            preAuthMap.put("status", preAuth.getStatus().name());
            preAuthMap.put("tpaReferenceNumber", preAuth.getTpaReferenceNumber());
            preAuthMap.put("approvedAmount", preAuth.getApprovedAmount());
            preAuthMap.put("requestedAt", preAuth.getRequestedAt() != null ? preAuth.getRequestedAt().toString() : null);

            Map<String, Object> policyMap = null;
            InsurancePolicy policy = preAuth.getInsurancePolicy();
            if (policy != null) {
                policyMap = new HashMap<>();
                policyMap.put("policyNumber", policy.getPolicyNumber());
                policyMap.put("memberId", policy.getMemberId());
                policyMap.put("sumInsured", policy.getSumInsured());
                policyMap.put("copayPct", policy.getCopayPct());
            }
            preAuthMap.put("insurancePolicy", policyMap);
        }

        List<Map<String, Object>> bedHistory = new ArrayList<>();
        if (admission != null) {
            List<BedAllocation> allocations = allocationRepository
                    .findByAdmissionIdAndTenantIdOrderByStartTimeDesc(admission.getId(), tenantId);
            for (BedAllocation alloc : allocations) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("bedNumber", alloc.getBed().getBedNumber());
                m.put("wardName", alloc.getBed().getRoom().getWard().getName());
                m.put("roomType", alloc.getBed().getRoom().getRoomType() != null ? alloc.getBed().getRoom().getRoomType().name() : null);
                m.put("startTime", alloc.getStartTime() != null ? alloc.getStartTime().toString() : null);
                m.put("endTime", alloc.getEndTime() != null ? alloc.getEndTime().toString() : null);
                m.put("isCurrent", alloc.getIsCurrent());
                m.put("transferReason", alloc.getTransferReason());
                m.put("dailyPrice", alloc.getDailyPriceAtTime());
                bedHistory.add(m);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bedHistory", bedHistory);
        result.put("patientId", patientId);
        result.put("admissionId", admission != null ? admission.getId() : null);
        result.put("admissionNumber", admission != null ? admission.getAdmissionNumber() : "OPD");
        result.put("invoiceNumber", !pendingBills.isEmpty() ? pendingBills.get(0).getInvoiceNumber() : null);
        result.put("patientName", patient.getFirstName() + " " + patient.getLastName());
        result.put("patientCode", patient.getPatientCode());
        result.put("status", admission != null ? admission.getStatus().name() : "OPD");
        result.put("isFrozen", isFrozen);
        result.put("items", items);
        result.put("sectionBreakdowns", sectionBreakdowns);
        result.put("totalCharges", totalCharges);
        result.put("totalDiscount", totalDiscount);
        boolean requireApproval = true;
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant != null && tenant.getSettings() != null) {
            Object val = tenant.getSettings().get("requireDiscountApproval");
            if (val instanceof Boolean) {
                requireApproval = (Boolean) val;
            } else if (val instanceof String) {
                requireApproval = Boolean.parseBoolean((String) val);
            }
        }

        boolean discountApproved = pendingBills.stream().allMatch(b -> b.getDiscountApproved() == null || b.getDiscountApproved());
        if (!requireApproval) {
            discountApproved = true;
        }
        result.put("discountApproved", discountApproved);
        String discountFeedback = pendingBills.stream().map(Billing::getDiscountFeedback).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        result.put("discountFeedback", discountFeedback);
        result.put("depositPaid", depositPaid);
        result.put("totalPaid", totalPaid.add(depositPaid));
        result.put("copayCollected", copayCollected);
        result.put("provisionalBalance", provisionalBalance);
        
        boolean allPaid = !pendingBills.isEmpty() && pendingBills.stream().allMatch(b -> b.getPaymentStatus() == PaymentStatus.PAID);
        result.put("paymentStatus", allPaid ? "PAID" : "PENDING");
        
        result.put("admissionTime", admission != null && admission.getAdmissionTime() != null ? admission.getAdmissionTime().toString() : null);
        result.put("primaryDoctorName", primaryDoctorName);
        result.put("primaryDoctorSpecialty", primaryDoctorSpecialty);
        result.put("currentWardName", currentWardName);
        result.put("currentBedNumber", currentBedNumber);
        result.put("stayDays", stayDays);
        result.put("depositReceiptNumber", null);
        result.put("preAuth", preAuthMap);
        
        result.put("dischargeCleared", admission == null || admission.isDischargeCleared());
        result.put("invoiceGenerated", admission == null || admission.isInvoiceGenerated());
        return result;
    }

    @Transactional
    public Map<String, Object> applySectionDiscount(Long patientId, SectionDiscountRequest req) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = getActiveAdmission(patientId, tenantId);
        Billing bill = getPrimaryBill(patientId, tenantId, admission);


        if (bill.getRemarks() != null && bill.getRemarks().startsWith("FROZEN")) {
            throw new InvalidStateTransitionException("Billing", "FROZEN", "APPLY_SECTION_DISCOUNT");
        }

        String section = req.getSection().toUpperCase();
        BigDecimal discount = req.getDiscount() != null ? req.getDiscount() : BigDecimal.ZERO;

        // Calculate gross charges for this section
        BigDecimal sectionGross = BigDecimal.ZERO;
        if (bill.getItems() != null) {
            for (BillingItem item : bill.getItems()) {
                if (getSectionKey(item.getItemType()).equals(section)) {
                    BigDecimal itemTotal = item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity()));
                    sectionGross = sectionGross.add(itemTotal);
                }
            }
        }

        if (discount.compareTo(sectionGross) > 0) {
            throw new IllegalArgumentException("Discount cannot exceed the gross charges of " + getSectionLabel(section) + " (₹" + sectionGross + ")");
        }

        Map<String, BigDecimal> sectionDiscounts = bill.getSectionDiscountsMap();
        if (discount.compareTo(BigDecimal.ZERO) == 0) {
            sectionDiscounts.remove(section);
        } else {
            sectionDiscounts.put(section, discount);
        }
        bill.setSectionDiscountsMap(sectionDiscounts);

        recalcNet(bill);

        boolean requireApproval = true;
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant != null && tenant.getSettings() != null) {
            Object val = tenant.getSettings().get("requireDiscountApproval");
            if (val instanceof Boolean) {
                requireApproval = (Boolean) val;
            } else if (val instanceof String) {
                requireApproval = Boolean.parseBoolean((String) val);
            }
        }

        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin || bill.getDiscount().compareTo(BigDecimal.ZERO) == 0 || !requireApproval) {
            bill.setDiscountApproved(true);
            bill.setDiscountFeedback(null);
        } else {
            bill.setDiscountApproved(false);
            bill.setDiscountFeedback(null);
        }

        billingRepository.save(bill);

        log.info("Section discount applied for patient {}: Section {}, Discount ₹{}", patientId, section, discount);

        // SSE: notify admins when a receptionist applies a discount that needs approval
        if (!isAdmin && bill.getDiscount().compareTo(BigDecimal.ZERO) > 0 && requireApproval) {
            Patient p = bill.getPatient();
            String patientName = p != null ? (p.getFirstName() + " " + p.getLastName()) : "Patient #" + patientId;
            queueEventService.broadcastDiscountRequested(tenantId, patientId, patientName, bill.getDiscount(), req.getTargetAdminId());
        }


        PreAuthRequest preAuth = admission != null ? loadPreAuth(admission.getId(), tenantId) : null;
        BedAllocation currentAllocation = admission != null ? allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admission.getId(), tenantId).orElse(null) : null;
        return buildUnifiedRunningBillSummary(patientId, admission, getRelevantBillsForSummary(patientId, tenantId, admission), preAuth, currentAllocation);
    }

    @Transactional
    public Map<String, Object> respondDiscount(Long patientId, RespondDiscountRequest request) {
        Long tenantId = TenantContext.getTenantId();
        IpdAdmission admission = getActiveAdmission(patientId, tenantId);
        Billing bill = getPrimaryBill(patientId, tenantId, admission);


        bill.setDiscountApproved(request.getApproved());
        bill.setDiscountFeedback(request.getFeedback());
        billingRepository.save(bill);
        log.info("Discount {} by admin for patient {}. Feedback: {}", request.getApproved() ? "approved" : "rejected", patientId, request.getFeedback());

        // SSE: notify receptionists that the discount has been responded to
        Patient p = bill.getPatient();
        String patientName = p != null ? (p.getFirstName() + " " + p.getLastName()) : "Patient #" + patientId;
        queueEventService.broadcastDiscountResponded(tenantId, patientId, patientName, request.getApproved(), request.getFeedback());

        PreAuthRequest preAuth = admission != null ? loadPreAuth(admission.getId(), tenantId) : null;
        BedAllocation currentAllocation = admission != null ? allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admission.getId(), tenantId).orElse(null) : null;
        return buildUnifiedRunningBillSummary(patientId, admission, getRelevantBillsForSummary(patientId, tenantId, admission), preAuth, currentAllocation);
    }

    public List<Map<String, Object>> getAdmins() {
        Long tenantId = TenantContext.getTenantId();
        return userRepository.findAllByTenantId(tenantId).stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.getName().name().equals("ROLE_ADMIN")))
                .map(u -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", u.getId());
                    map.put("fullName", u.getFullName());
                    map.put("email", u.getEmail());
                    return map;
                })
                .collect(Collectors.toList());
    }

    private void recalcNet(Billing bill) {
        // Compute section gross amounts first
        Map<String, BigDecimal> sectionGrossMap = new HashMap<>();
        for (String section : List.of("ROOM_NURSING", "PROCEDURE", "DOCTOR", "LAB", "MEDICINE", "OTHER")) {
            sectionGrossMap.put(section, BigDecimal.ZERO);
        }
        if (bill.getItems() != null) {
            for (BillingItem item : bill.getItems()) {
                String section = getSectionKey(item.getItemType());
                BigDecimal itemTotal = item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity()));
                sectionGrossMap.put(section, sectionGrossMap.getOrDefault(section, BigDecimal.ZERO).add(itemTotal));
            }
        }

        // Compute total discount based on section discounts, capped at each section's gross amount
        Map<String, BigDecimal> sectionDiscounts = bill.getSectionDiscountsMap();
        BigDecimal totalDiscount = BigDecimal.ZERO;
        Map<String, BigDecimal> updatedDiscounts = new HashMap<>();
        boolean changed = false;

        for (Map.Entry<String, BigDecimal> entry : sectionDiscounts.entrySet()) {
            String section = entry.getKey();
            BigDecimal discountVal = entry.getValue();
            BigDecimal grossVal = sectionGrossMap.getOrDefault(section, BigDecimal.ZERO);
            if (discountVal.compareTo(grossVal) > 0) {
                discountVal = grossVal;
                changed = true;
            }
            if (discountVal.compareTo(BigDecimal.ZERO) > 0) {
                totalDiscount = totalDiscount.add(discountVal);
                updatedDiscounts.put(section, discountVal);
            }
        }

        if (changed) {
            bill.setSectionDiscountsMap(updatedDiscounts);
        }

        bill.setDiscount(totalDiscount);

        BigDecimal net = bill.getTotalAmount()
                .subtract(bill.getDiscount() != null ? bill.getDiscount() : BigDecimal.ZERO)
                .subtract(bill.getInsuranceAdjustment() != null ? bill.getInsuranceAdjustment() : BigDecimal.ZERO)
                .add(bill.getTax() != null ? bill.getTax() : BigDecimal.ZERO);
        bill.setNetAmount(net.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : net);

        // Update paymentStatus dynamically based on netAmount vs paidAmount
        if (bill.getNetAmount().compareTo(bill.getPaidAmount()) <= 0) {
            bill.setPaymentStatus(PaymentStatus.PAID);
            if (bill.getPaidAt() == null) {
                bill.setPaidAt(LocalDateTime.now());
            }
        } else if (bill.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            bill.setPaymentStatus(PaymentStatus.PARTIAL);
            bill.setPaidAt(null);
        } else {
            bill.setPaymentStatus(PaymentStatus.PENDING);
            bill.setPaidAt(null);
        }
    }

    private String getSectionKey(BillingItemType type) {
        if (type == null) {
            return "OTHER";
        }
        return switch (type) {
            case BED_CHARGE, ICU_CHARGE, NURSING_CHARGE, DIET_CHARGE -> "ROOM_NURSING";
            case PROCEDURE, SURGERY, ANAESTHESIA -> "PROCEDURE";
            case CONSULTATION, IPD_CONSULTATION -> "DOCTOR";
            case LAB, RADIOLOGY -> "LAB";
            case MEDICINE -> "MEDICINE";
            default -> "OTHER";
        };
    }

    private String getSectionLabel(String key) {
        return switch (key) {
            case "ROOM_NURSING" -> "Room & Nursing";
            case "PROCEDURE"    -> "Procedure & Surgery";
            case "DOCTOR"       -> "Doctor Visits";
            case "LAB"          -> "Diagnostics & Lab";
            case "MEDICINE"     -> "Medicines & Pharmacy";
            default             -> "Other Charges";
        };
    }

    private BillingItemType mapChargeCategory(String category) {
        return switch (category.toUpperCase()) {
            case "ROOM_RENT", "BED_CHARGE"    -> BillingItemType.BED_CHARGE;
            case "NURSING", "NURSING_CHARGE"  -> BillingItemType.NURSING_CHARGE;
            case "DOCTOR_VISIT", "IPD_CONSULTATION" -> BillingItemType.IPD_CONSULTATION;
            case "MEDICINE"                   -> BillingItemType.MEDICINE;
            case "LAB_TEST", "LAB"            -> BillingItemType.LAB;
            case "PROCEDURE"                  -> BillingItemType.PROCEDURE;
            case "OT", "SURGERY"              -> BillingItemType.SURGERY;
            case "CONSULTATION"               -> BillingItemType.CONSULTATION;
            case "ANAESTHESIA"                -> BillingItemType.ANAESTHESIA;
            case "PHYSIOTHERAPY"              -> BillingItemType.PHYSIOTHERAPY;
            default                           -> BillingItemType.OTHER;
        };
    }
}
