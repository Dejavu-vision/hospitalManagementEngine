package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.IpdChargeRequest;
import com.curamatrix.hsm.dto.request.IpdSettlementRequest;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.*;
import com.curamatrix.hsm.exception.InvalidStateTransitionException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
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
 * IPD-specific billing operations:
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

    // ── Add a manual charge to the running bill ───────────────────────────────

    @Transactional
    public Map<String, Object> addCharge(Long admissionId, IpdChargeRequest req) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission", "id", admissionId));

        if (admission.getStatus() == AdmissionStatus.DISCHARGED) {
            throw new InvalidStateTransitionException("IPD Billing", "DISCHARGED", "ADD_CHARGE");
        }

        Billing bill = billingRepository.findByIpdAdmissionId(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Running Bill", "admissionId", admissionId));

        if (bill.getPaymentStatus() == PaymentStatus.PAID) {
            throw new InvalidStateTransitionException("IPD Billing", "PAID", "ADD_CHARGE");
        }

        BillingItemType itemType = mapChargeCategory(req.getChargeCategory());
        int qty = req.getQuantity() != null ? req.getQuantity() : 1;
        BigDecimal total = req.getUnitPrice().multiply(BigDecimal.valueOf(qty));

        BillingItem item = BillingItem.builder()
                .billing(bill)
                .description(req.getDescription())
                .amount(req.getUnitPrice())
                .quantity(qty)
                .itemType(itemType)
                .build();

        bill.getItems().add(item);
        bill.setTotalAmount(bill.getTotalAmount().add(total));
        recalcNet(bill);
        billingRepository.save(bill);

        log.info("IPD charge added to admission {}: {} x{} = ₹{}", admissionId, req.getDescription(), qty, total);

        PreAuthRequest preAuth = loadPreAuth(admissionId, tenantId);
        BedAllocation currentAllocation = allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admissionId, tenantId).orElse(null);
        return buildEnrichedRunningBillSummary(admission, bill, preAuth, currentAllocation);
    }

    // ── Remove a manual charge (only before freeze) ───────────────────────────

    @Transactional
    public Map<String, Object> removeCharge(Long admissionId, Long itemId) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission", "id", admissionId));

        Billing bill = billingRepository.findByIpdAdmissionId(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Running Bill", "admissionId", admissionId));

        if (bill.getPaymentStatus() == PaymentStatus.PAID) {
            throw new InvalidStateTransitionException("IPD Billing", "PAID", "REMOVE_CHARGE");
        }

        BillingItem toRemove = bill.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("BillingItem", "id", itemId));

        // Prevent removing auto-generated bed charges
        if (toRemove.getItemType() == BillingItemType.BED_CHARGE ||
            toRemove.getItemType() == BillingItemType.NURSING_CHARGE) {
            throw new IllegalArgumentException("Auto-generated bed/nursing charges cannot be removed manually.");
        }

        BigDecimal itemTotal = toRemove.getAmount().multiply(BigDecimal.valueOf(toRemove.getQuantity()));
        bill.getItems().remove(toRemove);
        bill.setTotalAmount(bill.getTotalAmount().subtract(itemTotal));
        recalcNet(bill);
        billingRepository.save(bill);

        log.info("IPD charge {} removed from admission {}", itemId, admissionId);

        PreAuthRequest preAuth = loadPreAuth(admissionId, tenantId);
        BedAllocation currentAllocation = allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admissionId, tenantId).orElse(null);
        return buildEnrichedRunningBillSummary(admission, bill, preAuth, currentAllocation);
    }

    // ── Get running bill summary (enriched) ──────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getRunningBill(Long admissionId) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission", "id", admissionId));

        Billing bill = billingRepository.findByIpdAdmissionId(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Running Bill", "admissionId", admissionId));

        PreAuthRequest preAuth = loadPreAuth(admissionId, tenantId);
        BedAllocation currentAllocation = allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admissionId, tenantId).orElse(null);

        return buildEnrichedRunningBillSummary(admission, bill, preAuth, currentAllocation);
    }

    // ── Clear discharge (doctor marks clinical work done) ─────────────────────

    @Transactional
    public Map<String, Object> clearDischarge(Long admissionId) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission", "id", admissionId));

        if (admission.getStatus() == AdmissionStatus.DISCHARGED) {
            throw new InvalidStateTransitionException("Admission", "DISCHARGED", "CLEAR_DISCHARGE");
        }

        admission.setDischargeCleared(true);
        admissionRepository.save(admission);
        log.info("Discharge cleared for admission {}", admissionId);

        Billing bill = billingRepository.findByIpdAdmissionId(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Running Bill", "admissionId", admissionId));
        PreAuthRequest preAuth = loadPreAuth(admissionId, tenantId);
        BedAllocation currentAllocation = allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admissionId, tenantId).orElse(null);
        return buildEnrichedRunningBillSummary(admission, bill, preAuth, currentAllocation);
    }

    // ── Generate Invoice (freeze + mark invoice generated) ───────────────────

    @Transactional
    public Map<String, Object> generateInvoice(Long admissionId) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission", "id", admissionId));

        if (admission.getStatus() == AdmissionStatus.DISCHARGED) {
            throw new InvalidStateTransitionException("Admission", "DISCHARGED", "GENERATE_INVOICE");
        }

        if (!admission.isDischargeCleared()) {
            throw new InvalidStateTransitionException("Admission", "DISCHARGE_NOT_CLEARED", "GENERATE_INVOICE");
        }

        Billing bill = billingRepository.findByIpdAdmissionId(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Running Bill", "admissionId", admissionId));

        // Freeze the bill if not already frozen
        if (bill.getRemarks() == null || !bill.getRemarks().startsWith("FROZEN")) {
            bill.setRemarks("FROZEN - " + LocalDateTime.now());
        }
        billingRepository.save(bill);

        // Mark invoice as generated
        admission.setInvoiceGenerated(true);
        admissionRepository.save(admission);
        log.info("Invoice generated for admission {}", admissionId);

        PreAuthRequest preAuth = loadPreAuth(admissionId, tenantId);
        BedAllocation currentAllocation = allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admissionId, tenantId).orElse(null);
        return buildEnrichedRunningBillSummary(admission, bill, preAuth, currentAllocation);
    }

    // ── Freeze bill (lock before discharge) ──────────────────────────────────

    @Transactional
    public Map<String, Object> freezeBill(Long admissionId) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission", "id", admissionId));

        if (admission.getStatus() != AdmissionStatus.ADMITTED) {
            throw new InvalidStateTransitionException("Admission", admission.getStatus().name(), "FREEZE");
        }

        Billing bill = billingRepository.findByIpdAdmissionId(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Running Bill", "admissionId", admissionId));

        // Mark all items as final by setting remarks
        bill.setRemarks("FROZEN - " + LocalDateTime.now());
        billingRepository.save(bill);

        log.info("IPD bill frozen for admission {}", admissionId);

        PreAuthRequest preAuth = loadPreAuth(admissionId, tenantId);
        BedAllocation currentAllocation = allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admissionId, tenantId).orElse(null);
        return buildEnrichedRunningBillSummary(admission, bill, preAuth, currentAllocation);
    }

    // ── Final settlement and discharge ────────────────────────────────────────

    @Transactional
    public Map<String, Object> finalSettlement(Long admissionId, IpdSettlementRequest req) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission", "id", admissionId));

        if (admission.getStatus() == AdmissionStatus.DISCHARGED) {
            throw new InvalidStateTransitionException("Admission", "DISCHARGED", "SETTLE");
        }

        Billing bill = billingRepository.findByIpdAdmissionId(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Running Bill", "admissionId", admissionId));

        // Calculate balance
        BigDecimal totalCharges = bill.getTotalAmount();
        BigDecimal alreadyPaid = bill.getPaidAmount();

        // Collect balance payment if any
        if (req.getAmount() != null && req.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal newPaid = alreadyPaid.add(req.getAmount());
            bill.setPaidAmount(newPaid);
        }

        // Mark bill as paid/closed
        bill.setPaymentStatus(PaymentStatus.PAID);
        bill.setPaidAt(LocalDateTime.now());
        bill.setNetAmount(totalCharges);
        billingRepository.save(bill);

        // Discharge the patient
        BedAllocation alloc = allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(admissionId, tenantId).orElse(null);
        String bedNumber = null;
        if (alloc != null) {
            alloc.setIsCurrent(false);
            alloc.setEndTime(LocalDateTime.now());
            allocationRepository.save(alloc);

            Bed bed = alloc.getBed();
            bedNumber = bed.getBedNumber();
            bed.setStatus(BedStatus.CLEANING);
            bedRepository.save(bed);
        }

        admission.setStatus(AdmissionStatus.DISCHARGED);
        admission.setActualDischargeTime(LocalDateTime.now());
        admissionRepository.save(admission);

        log.info("IPD final settlement complete for admission {}. Bed {} released.", admissionId, bedNumber);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("admissionId", admissionId);
        result.put("admissionNumber", admission.getAdmissionNumber());
        result.put("status", "DISCHARGED");
        result.put("totalCharges", totalCharges);
        result.put("totalPaid", bill.getPaidAmount());
        result.put("balance", totalCharges.subtract(bill.getPaidAmount()));
        result.put("bedReleased", bedNumber);
        result.put("dischargeTime", admission.getActualDischargeTime().toString());
        return result;
    }

    // ── Final bill breakdown ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getFinalBill(Long admissionId) {
        IpdAdmission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission", "id", admissionId));

        Billing bill = billingRepository.findByIpdAdmissionId(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Running Bill", "admissionId", admissionId));

        // Get pre-auth approved amount if any
        BigDecimal tpaApprovedAmount = BigDecimal.ZERO;
        if (admission.getPreAuthId() != null) {
            tpaApprovedAmount = bill.getInsuranceAdjustment() != null ? bill.getInsuranceAdjustment() : BigDecimal.ZERO;
        }

        BigDecimal totalCharges = bill.getTotalAmount();
        BigDecimal depositPaid = admission.getDepositAmount() != null ? admission.getDepositAmount() : BigDecimal.ZERO;
        BigDecimal copayCollected = bill.getPaidAmount().subtract(depositPaid);
        if (copayCollected.compareTo(BigDecimal.ZERO) < 0) copayCollected = BigDecimal.ZERO;

        BigDecimal balanceDue = totalCharges
                .subtract(depositPaid)
                .subtract(tpaApprovedAmount)
                .subtract(copayCollected);

        // Group items by category
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (BillingItem item : bill.getItems()) {
            String cat = item.getItemType().name();
            BigDecimal subtotal = item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity()));
            byCategory.merge(cat, subtotal, BigDecimal::add);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("admissionId", admissionId);
        result.put("admissionNumber", admission.getAdmissionNumber());
        result.put("patientName", admission.getPatient().getFirstName() + " " + admission.getPatient().getLastName());
        result.put("patientCode", admission.getPatient().getPatientCode());
        result.put("admissionTime", admission.getAdmissionTime() != null ? admission.getAdmissionTime().toString() : null);
        result.put("dischargeTime", admission.getActualDischargeTime() != null ? admission.getActualDischargeTime().toString() : null);
        result.put("chargesByCategory", byCategory);
        result.put("totalCharges", totalCharges);
        result.put("depositPaid", depositPaid);
        result.put("tpaApprovedAmount", tpaApprovedAmount);
        result.put("copayCollected", copayCollected);
        result.put("balanceDue", balanceDue);
        result.put("isFrozen", bill.getRemarks() != null && bill.getRemarks().startsWith("FROZEN"));
        result.put("paymentStatus", bill.getPaymentStatus().name());
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Load the most recent pre-auth for this admission (null if none).
     */
    private PreAuthRequest loadPreAuth(Long admissionId, Long tenantId) {
        List<PreAuthRequest> preAuths = preAuthRequestRepository
                .findByAdmissionIdAndTenantId(admissionId, tenantId);
        return preAuths.isEmpty() ? null : preAuths.get(0);
    }

    /**
     * Build the enriched running bill summary including TPA/insurance, doctor,
     * ward/bed, and stay-duration data.
     *
     * NEVER put raw LocalDateTime into a Map — always call .toString() explicitly.
     * NEVER use Map.of() — values may be null (use HashMap / LinkedHashMap).
     */
    private Map<String, Object> buildEnrichedRunningBillSummary(
            IpdAdmission admission,
            Billing bill,
            PreAuthRequest preAuth,          // nullable
            BedAllocation currentAllocation  // nullable
    ) {
        // ── Items ──────────────────────────────────────────────────────────────
        List<Map<String, Object>> items = bill.getItems().stream().map(item -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", item.getId());
            m.put("description", item.getDescription());
            m.put("chargeCategory", item.getItemType().name());
            m.put("unitPrice", item.getAmount());
            m.put("quantity", item.getQuantity());
            m.put("totalAmount", item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity())));
            m.put("isAutoCharge", item.getItemType() == BillingItemType.BED_CHARGE ||
                                   item.getItemType() == BillingItemType.NURSING_CHARGE);
            return m;
        }).collect(Collectors.toList());

        // ── Financial totals ───────────────────────────────────────────────────
        BigDecimal depositPaid = admission.getDepositAmount() != null ? admission.getDepositAmount() : BigDecimal.ZERO;
        BigDecimal provisionalBalance = bill.getTotalAmount().subtract(depositPaid);

        // ── Stay duration (always ≥ 1) ─────────────────────────────────────────
        int stayDays = (int) ChronoUnit.DAYS.between(
                admission.getAdmissionTime().toLocalDate(), LocalDate.now()) + 1;
        if (stayDays < 1) stayDays = 1;

        // ── Doctor info ────────────────────────────────────────────────────────
        String primaryDoctorName = null;
        String primaryDoctorSpecialty = null;
        try {
            Doctor doctor = admission.getPrimaryDoctor();
            if (doctor != null) {
                primaryDoctorName = doctor.getUser().getFullName();
                if (doctor.getDepartment() != null) {
                    primaryDoctorSpecialty = doctor.getDepartment().getName();
                }
            }
        } catch (Exception e) {
            log.warn("Could not load doctor info for admission {}: {}", admission.getId(), e.getMessage());
        }

        // ── Ward / Bed info ────────────────────────────────────────────────────
        String currentWardName = null;
        String currentBedNumber = null;
        if (currentAllocation != null) {
            try {
                Bed bed = currentAllocation.getBed();
                currentBedNumber = bed.getBedNumber();
                if (bed.getRoom() != null && bed.getRoom().getWard() != null) {
                    currentWardName = bed.getRoom().getWard().getName();
                }
            } catch (Exception e) {
                log.warn("Could not load ward/bed info for admission {}: {}", admission.getId(), e.getMessage());
            }
        }

        // ── Deposit receipt number ─────────────────────────────────────────────
        String depositReceiptNumber = null;
        try {
            Long tenantId = TenantContext.getTenantId();
            List<Payment> payments = paymentRepository
                    .findAllByPatientIdAndTenantId(admission.getPatient().getId(), tenantId);
            if (!payments.isEmpty()) {
                // Prefer receiptNumber, fall back to referenceNumber
                depositReceiptNumber = payments.stream()
                        .filter(p -> p.getReceiptNumber() != null)
                        .map(Payment::getReceiptNumber)
                        .findFirst()
                        .orElseGet(() -> payments.stream()
                                .filter(p -> p.getReferenceNumber() != null)
                                .map(Payment::getReferenceNumber)
                                .findFirst()
                                .orElse(null));
            }
        } catch (Exception e) {
            log.warn("Could not load deposit receipt for admission {}: {}", admission.getId(), e.getMessage());
        }

        // ── Pre-auth sub-map (use HashMap — values may be null) ────────────────
        Map<String, Object> preAuthMap = null;
        if (preAuth != null) {
            preAuthMap = new HashMap<>();
            preAuthMap.put("id", preAuth.getId());
            preAuthMap.put("status", preAuth.getStatus().name());
            preAuthMap.put("tpaReferenceNumber", preAuth.getTpaReferenceNumber());
            preAuthMap.put("approvedAmount", preAuth.getApprovedAmount());
            // Explicitly call .toString() on LocalDateTime — never put raw LocalDateTime in Map
            preAuthMap.put("requestedAt", preAuth.getRequestedAt() != null
                    ? preAuth.getRequestedAt().toString() : null);

            // Insurance policy sub-map
            Map<String, Object> policyMap = null;
            InsurancePolicy policy = preAuth.getInsurancePolicy();
            if (policy != null) {
                policyMap = new HashMap<>();
                policyMap.put("policyNumber", policy.getPolicyNumber());
                policyMap.put("memberId", policy.getMemberId());
                policyMap.put("sumInsured", policy.getSumInsured());
                policyMap.put("copayPct", policy.getCopayPct());
                // Payer info
                PayerMaster payer = policy.getPayer();
                policyMap.put("insurerName", payer != null ? payer.getInsurerName() : null);
                policyMap.put("tpaName", payer != null ? payer.getTpaName() : null);
            }
            preAuthMap.put("insurancePolicy", policyMap);
        }

        // ── Assemble result ────────────────────────────────────────────────────
        Map<String, Object> result = new LinkedHashMap<>();
        // Existing fields
        result.put("admissionId", admission.getId());
        result.put("admissionNumber", admission.getAdmissionNumber());
        result.put("patientName", admission.getPatient().getFirstName() + " " + admission.getPatient().getLastName());
        result.put("patientCode", admission.getPatient().getPatientCode());
        result.put("status", admission.getStatus().name());
        result.put("isFrozen", bill.getRemarks() != null && bill.getRemarks().startsWith("FROZEN"));
        result.put("items", items);
        result.put("totalCharges", bill.getTotalAmount());
        result.put("depositPaid", depositPaid);
        result.put("provisionalBalance", provisionalBalance);
        result.put("paymentStatus", bill.getPaymentStatus().name());
        // New enriched fields — explicitly call .toString() on LocalDateTime
        result.put("admissionTime", admission.getAdmissionTime() != null
                ? admission.getAdmissionTime().toString() : null);
        result.put("primaryDoctorName", primaryDoctorName);
        result.put("primaryDoctorSpecialty", primaryDoctorSpecialty);
        result.put("currentWardName", currentWardName);
        result.put("currentBedNumber", currentBedNumber);
        result.put("stayDays", stayDays);
        result.put("depositReceiptNumber", depositReceiptNumber);
        result.put("preAuth", preAuthMap);
        // Unlock gate fields for frontend button logic
        result.put("dischargeCleared", admission.isDischargeCleared());
        result.put("invoiceGenerated", admission.isInvoiceGenerated());
        return result;
    }

    private void recalcNet(Billing bill) {
        BigDecimal net = bill.getTotalAmount()
                .subtract(bill.getDiscount() != null ? bill.getDiscount() : BigDecimal.ZERO)
                .subtract(bill.getInsuranceAdjustment() != null ? bill.getInsuranceAdjustment() : BigDecimal.ZERO)
                .add(bill.getTax() != null ? bill.getTax() : BigDecimal.ZERO);
        bill.setNetAmount(net.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : net);
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
