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

    // ── Unified Lookup Helpers ────────────────────────────────────────────────

    private IpdAdmission getActiveAdmission(Long patientId, Long tenantId) {
        return admissionRepository.findByStatusAndTenantId(AdmissionStatus.ADMITTED, tenantId).stream()
                .filter(a -> a.getPatient().getId().equals(patientId))
                .findFirst().orElse(null);
    }

    private List<Billing> getAllPendingBills(Long patientId, Long tenantId) {
        return billingRepository.findAllByPatientIdAndTenantId(patientId, tenantId).stream()
                .filter(b -> b.getPaymentStatus() == PaymentStatus.PENDING || b.getPaymentStatus() == PaymentStatus.PARTIAL)
                .collect(Collectors.toList());
    }

    private List<Billing> getRelevantBillsForSummary(Long patientId, Long tenantId, IpdAdmission admission) {
        List<Billing> pendingBills = getAllPendingBills(patientId, tenantId);
        if (admission == null) {
            // Include today's paid bills for OPD so they show up in the running bill UI
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            List<Billing> todaysPaid = billingRepository.findAllByPatientIdAndTenantId(patientId, tenantId).stream()
                    .filter(b -> b.getPaymentStatus() == PaymentStatus.PAID && b.getCreatedAt() != null && !b.getCreatedAt().isBefore(startOfDay))
                    .collect(Collectors.toList());
            List<Billing> result = new ArrayList<>(pendingBills);
            for (Billing b : todaysPaid) {
                if (!result.contains(b)) result.add(b);
            }
            // Sort by createdAt so chronological
            result.sort(Comparator.comparing(Billing::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
            return result;
        }
        return pendingBills;
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

    // ── Add a manual charge to the running bill ───────────────────────────────

    @Transactional
    public Map<String, Object> addCharge(Long patientId, IpdChargeRequest req) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = getActiveAdmission(patientId, tenantId);
        Billing bill = getPrimaryBill(patientId, tenantId, admission);

        if (bill.getPaymentStatus() == PaymentStatus.PAID) {
            throw new InvalidStateTransitionException("Billing", "PAID", "ADD_CHARGE");
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

        log.info("Charge added for patient {}: {} x{} = ₹{}", patientId, req.getDescription(), qty, total);

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
        List<Billing> pendingBills = getAllPendingBills(patientId, tenantId);

        // Find the bill containing this item
        Billing bill = pendingBills.stream()
                .filter(b -> b.getItems().stream().anyMatch(i -> i.getId().equals(itemId)))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("BillingItem", "id", itemId));

        if (bill.getPaymentStatus() == PaymentStatus.PAID) {
            throw new InvalidStateTransitionException("Billing", "PAID", "REMOVE_CHARGE");
        }

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
            if (admission.getStatus() == AdmissionStatus.DISCHARGED) {
                throw new InvalidStateTransitionException("Admission", "DISCHARGED", "GENERATE_INVOICE");
            }
            if (!admission.isDischargeCleared()) {
                throw new InvalidStateTransitionException("Admission", "DISCHARGE_NOT_CLEARED", "GENERATE_INVOICE");
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

    // ── Final settlement and discharge ────────────────────────────────────────

    @Transactional
    public Map<String, Object> finalSettlement(Long patientId, IpdSettlementRequest req) {
        Long tenantId = TenantContext.getTenantId();

        IpdAdmission admission = getActiveAdmission(patientId, tenantId);
        List<Billing> pendingBills = getAllPendingBills(patientId, tenantId);

        if (admission != null) {
            if (admission.getStatus() == AdmissionStatus.DISCHARGED) {
                throw new InvalidStateTransitionException("Admission", "DISCHARGED", "SETTLE");
            }
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

        BigDecimal totalCharges = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        
        // Finalize all bills
        for (Billing bill : pendingBills) {
            bill.setPaymentStatus(PaymentStatus.PAID);
            bill.setPaidAt(LocalDateTime.now());
            if (req.getPaymentMethod() != null) {
                try {
                    bill.setPaymentMethod(PaymentMethod.valueOf(req.getPaymentMethod().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    bill.setPaymentMethod(PaymentMethod.CASH);
                }
            }
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

            admission.setStatus(AdmissionStatus.DISCHARGED);
            admission.setActualDischargeTime(LocalDateTime.now());
            admissionRepository.save(admission);
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
        result.put("totalPaid", totalPaid);
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
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        
        for (Billing bill : bills) {
            totalCharges = totalCharges.add(bill.getTotalAmount());
            totalPaid = totalPaid.add(bill.getPaidAmount());
            for (BillingItem item : bill.getItems()) {
                String cat = item.getItemType().name();
                BigDecimal subtotal = item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity()));
                byCategory.merge(cat, subtotal, BigDecimal::add);
            }
        }

        BigDecimal depositPaid = admission != null && admission.getDepositAmount() != null ? admission.getDepositAmount() : BigDecimal.ZERO;
        BigDecimal copayCollected = totalPaid.subtract(depositPaid);
        if (copayCollected.compareTo(BigDecimal.ZERO) < 0) copayCollected = BigDecimal.ZERO;

        BigDecimal balanceDue = totalCharges
                .subtract(depositPaid)
                .subtract(tpaApprovedAmount)
                .subtract(copayCollected);

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
        result.put("depositPaid", depositPaid);
        result.put("tpaApprovedAmount", tpaApprovedAmount);
        result.put("copayCollected", copayCollected);
        result.put("balanceDue", balanceDue);
        
        boolean isFrozen = bills.stream().anyMatch(b -> b.getRemarks() != null && b.getRemarks().startsWith("FROZEN"));
        result.put("isFrozen", isFrozen);
        
        boolean allPaid = bills.stream().allMatch(b -> b.getPaymentStatus() == PaymentStatus.PAID);
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
        Patient patient = patientRepository.findById(patientId).orElseThrow();
        
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal totalCharges = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        boolean isFrozen = false;
        
        for (Billing bill : pendingBills) {
            totalCharges = totalCharges.add(bill.getTotalAmount());
            totalPaid = totalPaid.add(bill.getPaidAmount());
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
                items.add(m);
            }
        }

        BigDecimal depositPaid = admission != null && admission.getDepositAmount() != null ? admission.getDepositAmount() : BigDecimal.ZERO;
        
        BigDecimal tpaApprovedAmount = BigDecimal.ZERO;
        if (preAuth != null && "APPROVED".equals(preAuth.getStatus().name())) {
            tpaApprovedAmount = preAuth.getApprovedAmount() != null ? preAuth.getApprovedAmount() : BigDecimal.ZERO;
        }
        
        BigDecimal provisionalBalance = totalCharges
                .subtract(depositPaid)
                .subtract(totalPaid)
                .subtract(tpaApprovedAmount);
                
        if (provisionalBalance.compareTo(BigDecimal.ZERO) < 0) {
            provisionalBalance = BigDecimal.ZERO;
        }

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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("patientId", patientId);
        result.put("admissionId", admission != null ? admission.getId() : null);
        result.put("admissionNumber", admission != null ? admission.getAdmissionNumber() : "OPD");
        result.put("invoiceNumber", !pendingBills.isEmpty() ? pendingBills.get(0).getInvoiceNumber() : null);
        result.put("patientName", patient.getFirstName() + " " + patient.getLastName());
        result.put("patientCode", patient.getPatientCode());
        result.put("status", admission != null ? admission.getStatus().name() : "OPD");
        result.put("isFrozen", isFrozen);
        result.put("items", items);
        result.put("totalCharges", totalCharges);
        result.put("depositPaid", depositPaid);
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
