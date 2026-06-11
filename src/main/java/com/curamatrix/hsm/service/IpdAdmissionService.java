package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.AdmissionRequest;
import com.curamatrix.hsm.dto.response.AdmissionResponse;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.entity.HospitalService;
import com.curamatrix.hsm.enums.*;
import com.curamatrix.hsm.exception.DuplicateResourceException;
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


@Slf4j
@Service
@RequiredArgsConstructor
public class IpdAdmissionService {

    private final IpdAdmissionRepository admissionRepository;
    private final IpdAdmissionSequenceRepository sequenceRepository;
    private final BedAllocationRepository allocationRepository;
    private final BedRepository bedRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final BillingRepository billingRepository;
    private final PreAuthRequestRepository preAuthRequestRepository;
    private final CatalogResolverService catalogResolver;

    @Transactional
    public AdmissionResponse admitPatient(AdmissionRequest request) {
        Long tenantId = TenantContext.getTenantId();

        // Validate deposit is non-negative
        if (request.getDepositAmount() != null && request.getDepositAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Deposit amount cannot be negative");
        }

        // Prevent double admission
        if (admissionRepository.existsByPatientIdAndStatusAndTenantId(request.getPatientId(), AdmissionStatus.ADMITTED, tenantId)) {
            throw new DuplicateResourceException("Patient is already admitted. Please discharge before re-admitting.");
        }

        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", request.getPatientId()));
        Doctor doctor = doctorRepository.findById(request.getPrimaryDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", request.getPrimaryDoctorId()));

        Appointment opd = null;
        if (request.getOpdAppointmentId() != null) {
            opd = appointmentRepository.findById(request.getOpdAppointmentId()).orElse(null);
        }

        Bed bed = bedRepository.findById(request.getBedId())
                .orElseThrow(() -> new ResourceNotFoundException("Bed", "id", request.getBedId()));

        if (bed.getStatus() != BedStatus.AVAILABLE && bed.getStatus() != BedStatus.CLEANING) {
            throw new InvalidStateTransitionException("Bed Allocation", bed.getStatus().name(), "OCCUPIED");
        }

        // Generate proper admission number (IPD-YYYY-NNNNN)
        String admNumber = generateAdmissionNumber(tenantId);

        // Create Admission
        IpdAdmission admission = IpdAdmission.builder()
                .admissionNumber(admNumber)
                .patient(patient)
                .primaryDoctor(doctor)
                .opdAppointment(opd)
                .admissionType(request.getAdmissionType())
                .status(AdmissionStatus.ADMITTED)
                .admissionTime(LocalDateTime.now())
                .expectedDischargeTime(request.getExpectedDischargeTime())
                .admissionNotes(request.getAdmissionNotes())
                .depositAmount(request.getDepositAmount())
                .paymentMethod(request.getPaymentMethod())
                .preAuthId(request.getPreAuthId())
                .build();
        admission.setTenantId(tenantId);
        admission = admissionRepository.save(admission);

        // Allocate Bed — mark OCCUPIED and snapshot daily price
        bed.setStatus(BedStatus.OCCUPIED);
        bedRepository.save(bed);

        BedAllocation allocation = BedAllocation.builder()
                .admission(admission)
                .bed(bed)
                .startTime(LocalDateTime.now())
                .isCurrent(true)
                .dailyPriceAtTime(catalogResolver.resolveBedCharge(bed.getRoom().getRoomType(), tenantId).getPrice())
                .nursingChargeAtTime(catalogResolver.resolveOptional("NURSING_CHARGE", tenantId).map(HospitalService::getPrice).orElse(null))
                .dietChargeAtTime(catalogResolver.resolveOptional("DIET_CHARGE", tenantId).map(HospitalService::getPrice).orElse(null))
                .build();
        allocation.setTenantId(tenantId);
        allocationRepository.save(allocation);

        // Create Running IPD Bill
        Billing ipdBilling = Billing.builder()
                .ipdAdmission(admission)
                .patient(patient)
                .invoiceNumber("IPD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .totalAmount(BigDecimal.ZERO)
                .netAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .paymentStatus(PaymentStatus.PENDING)
                .items(new ArrayList<>())
                .build();
        ipdBilling.setTenantId(tenantId);
        ipdBilling = billingRepository.save(ipdBilling);

        // Auto-post Day 1 bed charge from catalog snapshot
        BigDecimal bedRate = allocation.getDailyPriceAtTime();
        if (bedRate != null && bedRate.compareTo(BigDecimal.ZERO) > 0) {
            BillingItem bedCharge = BillingItem.builder()
                    .billing(ipdBilling)
                    .description("Bed Charge - " + bed.getBedNumber() + " (Day 1)")
                    .amount(bedRate)
                    .quantity(1)
                    .itemType(BillingItemType.BED_CHARGE)
                    .build();
            ipdBilling.getItems().add(bedCharge);
            ipdBilling.setTotalAmount(bedRate);
            ipdBilling.setNetAmount(bedRate);
            billingRepository.save(ipdBilling);
            log.info("Auto-posted Day 1 bed charge ₹{} for admission {}", bedRate, admNumber);
        }

        // Add deposit billing item if deposit > 0
        if (request.getDepositAmount() != null && request.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
            BillingItem depositItem = BillingItem.builder()
                    .billing(ipdBilling)
                    .description("Admission Deposit")
                    .amount(request.getDepositAmount())
                    .quantity(1)
                    .itemType(BillingItemType.DEPOSIT)
                    .build();
            ipdBilling.getItems().add(depositItem);
            // Keep totalAmount as-is (bed charge already set it); only update paidAmount
            ipdBilling.setPaidAmount(request.getDepositAmount());
            ipdBilling.setPaymentStatus(PaymentStatus.PARTIAL); // deposit received, bill not settled
            billingRepository.save(ipdBilling);
        }

        // Link pre-auth to this admission if provided
        if (request.getPreAuthId() != null) {
            final Long admissionId = admission.getId();
            preAuthRequestRepository.findById(request.getPreAuthId()).ifPresent(preAuth -> {
                preAuth.setAdmissionId(admissionId);
                preAuthRequestRepository.save(preAuth);
                log.info("Linked PreAuthRequest {} to Admission {}", preAuth.getId(), admissionId);
            });
        }

        log.info("Patient {} admitted with admission number {} to bed {}",
                patient.getId(), admNumber, bed.getBedNumber());

        return mapToResponse(admission, bed);
    }

    /**
     * Generates a unique admission number in the format IPD-YYYY-NNNNN.
     * Uses a pessimistic SELECT FOR UPDATE lock to prevent duplicates under concurrent load.
     */
    @Transactional
    public String generateAdmissionNumber(Long tenantId) {
        int year = LocalDate.now().getYear();
        IpdAdmissionSequence seq = sequenceRepository.findForUpdate(year, tenantId)
                .orElseGet(() -> {
                    IpdAdmissionSequence s = IpdAdmissionSequence.builder().year(year).build();
                    s.setTenantId(tenantId);
                    return s;
                });
        seq.setLastSequence(seq.getLastSequence() + 1);
        sequenceRepository.save(seq);
        return String.format("IPD-%d-%05d", year, seq.getLastSequence());
    }

    public AdmissionResponse getAdmission(Long id) {
        Long tenantId = TenantContext.getTenantId();
        IpdAdmission admission = admissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Admission", "id", id));

        Bed currentBed = null;
        BedAllocation alloc = allocationRepository.findByAdmissionIdAndIsCurrentTrueAndTenantId(id, tenantId).orElse(null);
        if (alloc != null) {
            currentBed = alloc.getBed();
        }

        return mapToResponse(admission, currentBed);
    }

    public AdmissionResponse getAdmissionByBedId(Long bedId) {
        Long tenantId = TenantContext.getTenantId();
        BedAllocation alloc = allocationRepository.findByBedIdAndIsCurrentTrueAndTenantId(bedId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed Allocation", "bedId", bedId));

        return mapToResponse(alloc.getAdmission(), alloc.getBed());
    }

    @Transactional
    public AdmissionResponse dischargePatient(Long admissionId, String dischargeSummary) {
        Long tenantId = TenantContext.getTenantId();
        IpdAdmission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission", "id", admissionId));

        if (admission.getStatus() == AdmissionStatus.DISCHARGED) {
            throw new InvalidStateTransitionException("Admission", admission.getStatus().name(), "DISCHARGED");
        }

        // Close the Bed Allocation
        BedAllocation alloc = allocationRepository.findByAdmissionIdAndIsCurrentTrueAndTenantId(admissionId, tenantId).orElse(null);
        if (alloc != null) {
            alloc.setIsCurrent(false);
            alloc.setEndTime(LocalDateTime.now());
            allocationRepository.save(alloc);

            Bed bed = alloc.getBed();
            bed.setStatus(BedStatus.CLEANING);
            bedRepository.save(bed);
        }

        // Close Admission
        admission.setStatus(AdmissionStatus.DISCHARGED);
        admission.setActualDischargeTime(LocalDateTime.now());
        admission.setDischargeSummary(dischargeSummary);
        admission = admissionRepository.save(admission);

        return mapToResponse(admission, null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getActiveAdmissions(String status, String fromDate, String toDate) {
        Long tenantId = TenantContext.getTenantId();
        boolean isPaidTab = "PAID".equalsIgnoreCase(status);

        // Parse date range if provided
        LocalDateTime startDate = fromDate != null && !fromDate.isEmpty()
                ? LocalDate.parse(fromDate).atStartOfDay() : null;
        LocalDateTime endDate = toDate != null && !toDate.isEmpty()
                ? LocalDate.parse(toDate).atTime(23, 59, 59) : null;

        List<Map<String, Object>> resultList = new ArrayList<>();
        Set<Long> processedPatientIds = new HashSet<>();

        if (isPaidTab) {
            // ── PAID TAB: Discharged IPD + Paid OPD bills ──────────────────
            // Default date range to today if none given (prevents loading all historical data)
            LocalDateTime effStart = startDate != null ? startDate : LocalDate.now().atStartOfDay();
            LocalDateTime effEnd   = endDate   != null ? endDate   : LocalDate.now().atTime(23, 59, 59);

            // Fetch all paid bills in the date range
            List<Billing> paidBills = billingRepository.findPaidBillsByDateRange(tenantId, effStart, effEnd);
            List<Billing> paidOpdBills = new ArrayList<>();

            for (Billing b : paidBills) {
                if (b.getIpdAdmission() != null) {
                    IpdAdmission adm = b.getIpdAdmission();
                    if (!processedPatientIds.contains(adm.getPatient().getId())) {
                        processedPatientIds.add(adm.getPatient().getId());
                        resultList.add(buildAdmissionRow(adm, b, tenantId, true));
                    }
                } else if (b.getPatient() != null) {
                    paidOpdBills.add(b);
                }
            }

            Map<Long, List<Billing>> billsByPatient = paidOpdBills.stream()
                    .filter(b -> !processedPatientIds.contains(b.getPatient().getId()))
                    .collect(Collectors.groupingBy(b -> b.getPatient().getId()));
            for (Map.Entry<Long, List<Billing>> entry : billsByPatient.entrySet()) {
                resultList.add(buildOpdRow(entry.getValue(), true));
            }

        } else {
            // ── PENDING TAB: Active admitted IPD + Discharged Unpaid IPD + Pending OPD bills ────────
            
            // 1. Active IPD admissions (status = ADMITTED)
            List<IpdAdmission> active;
            if (startDate != null && endDate != null) {
                active = admissionRepository.findAdmittedByTenantIdAndDateRange(tenantId, startDate, endDate);
            } else {
                active = admissionRepository.findByStatusAndTenantId(AdmissionStatus.ADMITTED, tenantId);
            }

            for (IpdAdmission adm : active) {
                processedPatientIds.add(adm.getPatient().getId());
                resultList.add(buildAdmissionRow(adm, null, tenantId, false));
            }

            List<PaymentStatus> pendingStatuses = List.of(PaymentStatus.PENDING, PaymentStatus.PARTIAL);

            // 2. Discharged IPD admissions whose bill is still PENDING or PARTIAL
            List<Billing> unpaidDischargedBills = billingRepository.findUnpaidDischargedBills(tenantId, pendingStatuses, startDate, endDate);
            for (Billing b : unpaidDischargedBills) {
                IpdAdmission adm = b.getIpdAdmission();
                if (adm != null && !processedPatientIds.contains(adm.getPatient().getId())) {
                    processedPatientIds.add(adm.getPatient().getId());
                    resultList.add(buildAdmissionRow(adm, b, tenantId, false));
                }
            }

            // 3. Pending OPD bills
            List<Billing> opdBills;
            if (startDate != null && endDate != null) {
                opdBills = billingRepository.findOpdBillsByStatusAndDateRange(tenantId, pendingStatuses, startDate, endDate);
            } else {
                opdBills = billingRepository.findPendingOpdBills(tenantId, pendingStatuses);
            }

            Map<Long, List<Billing>> billsByPatient = opdBills.stream()
                    .filter(b -> b.getPatient() != null && !processedPatientIds.contains(b.getPatient().getId()))
                    .collect(Collectors.groupingBy(b -> b.getPatient().getId()));
            for (Map.Entry<Long, List<Billing>> entry : billsByPatient.entrySet()) {
                resultList.add(buildOpdRow(entry.getValue(), false));
            }
        }

        return resultList;
    }

    private Map<String, Object> buildAdmissionRow(IpdAdmission adm, Billing bill, Long tenantId, boolean isPaid) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", adm.getId());
        row.put("patientId", adm.getPatient().getId());
        row.put("admissionNumber", adm.getAdmissionNumber());
        row.put("patientName", adm.getPatient().getFirstName() + " " + adm.getPatient().getLastName());
        row.put("patientCode", adm.getPatient().getPatientCode());
        row.put("primaryDoctorName", adm.getPrimaryDoctor().getUser().getFullName());
        row.put("admissionType", adm.getAdmissionType() != null ? adm.getAdmissionType().name() : "IPD");
        row.put("admissionTime", adm.getAdmissionTime() != null ? adm.getAdmissionTime().toString() : null);
        row.put("expectedDischargeTime", adm.getExpectedDischargeTime() != null ? adm.getExpectedDischargeTime().toString() : null);
        row.put("actualDischargeTime", adm.getActualDischargeTime() != null ? adm.getActualDischargeTime().toString() : null);

        long daysAdmitted = adm.getAdmissionTime() != null
                ? ChronoUnit.DAYS.between(adm.getAdmissionTime().toLocalDate(),
                  isPaid && adm.getActualDischargeTime() != null
                      ? adm.getActualDischargeTime().toLocalDate()
                      : LocalDateTime.now().toLocalDate()) + 1
                : 0;
        row.put("daysAdmitted", daysAdmitted);

        // Current bed info (for active) or last bed (for discharged)
        BedAllocation alloc = allocationRepository
                .findByAdmissionIdAndIsCurrentTrueAndTenantId(adm.getId(), tenantId).orElse(null);
        if (alloc != null) {
            Bed bed = alloc.getBed();
            row.put("bedId", bed.getId());
            row.put("bedNumber", bed.getBedNumber());
            row.put("wardName", bed.getRoom().getWard().getName());
            row.put("roomNumber", bed.getRoom().getRoomNumber());
            row.put("roomType", bed.getRoom().getRoomType().name());
            row.put("dailyPrice", alloc.getDailyPriceAtTime());
        }

        Billing finalBill = bill != null ? bill : billingRepository.findByIpdAdmissionId(adm.getId()).orElse(null);
        row.put("runningBillTotal", finalBill != null ? finalBill.getNetAmount() : BigDecimal.ZERO);
        row.put("paidAmount", finalBill != null ? finalBill.getPaidAmount() : BigDecimal.ZERO);
        row.put("paymentStatus", finalBill != null ? finalBill.getPaymentStatus().name() : "PENDING");
        return row;
    }

    private Map<String, Object> buildOpdRow(List<Billing> bills, boolean isPaid) {
        Patient p = bills.get(0).getPatient();

        BigDecimal totalNet = bills.stream()
                .map(b -> b.getNetAmount() != null ? b.getNetAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = bills.stream()
                .map(b -> b.getPaidAmount() != null ? b.getPaidAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Settlement date: latest paidAt among paid bills
        LocalDateTime settledAt = bills.stream()
                .map(Billing::getPaidAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        String docName = "OPD Consultant";
        for (Billing b : bills) {
            if (b.getAppointment() != null && b.getAppointment().getDoctor() != null) {
                docName = b.getAppointment().getDoctor().getUser().getFullName();
                break;
            }
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", null);
        row.put("patientId", p.getId());
        row.put("admissionNumber", "OPD");
        row.put("patientName", p.getFirstName() + " " + p.getLastName());
        row.put("patientCode", p.getPatientCode());
        row.put("primaryDoctorName", docName);
        row.put("admissionType", "OPD");
        row.put("bedNumber", "OPD");
        row.put("wardName", "Outpatient");
        row.put("roomType", "");
        row.put("daysAdmitted", 0);
        row.put("runningBillTotal", totalNet);
        row.put("paidAmount", totalPaid);
        row.put("paymentStatus", isPaid ? "PAID" : "PENDING");
        row.put("actualDischargeTime", settledAt != null ? settledAt.toString() : null);
        return row;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdmissionBilling(Long admissionId) {
        IpdAdmission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission", "id", admissionId));

        Billing billing = billingRepository.findByIpdAdmissionId(admissionId).orElse(null);
        if (billing == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("admissionId", admissionId);
            empty.put("message", "No running bill found for this admission.");
            return empty;
        }

        List<Map<String, Object>> items = billing.getItems().stream().map(item -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", item.getId());
            m.put("description", item.getDescription());
            m.put("amount", item.getAmount());
            m.put("quantity", item.getQuantity());
            m.put("itemType", item.getItemType() != null ? item.getItemType().name() : null);
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("billingId", billing.getId());
        result.put("invoiceNumber", billing.getInvoiceNumber());
        result.put("admissionId", admissionId);
        result.put("patientName", admission.getPatient().getFirstName() + " " + admission.getPatient().getLastName());
        result.put("totalAmount", billing.getTotalAmount());
        result.put("discount", billing.getDiscount());
        result.put("tax", billing.getTax());
        result.put("netAmount", billing.getNetAmount());
        result.put("paidAmount", billing.getPaidAmount());
        result.put("balance", billing.getNetAmount().subtract(billing.getPaidAmount()));
        result.put("paymentStatus", billing.getPaymentStatus().name());
        result.put("items", items);
        return result;
    }

    @Transactional
    public void transferBed(Long admissionId, Long newBedId, String transferReason) {
        Long tenantId = TenantContext.getTenantId();

        // 1. Get admission
        IpdAdmission admission = admissionRepository.findByIdAndTenantId(admissionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission", "id", admissionId));

        if (admission.getStatus() == AdmissionStatus.DISCHARGED) {
            throw new IllegalStateException("Cannot transfer bed for a discharged admission");
        }

        // 2. Get active allocation
        BedAllocation activeAlloc = allocationRepository.findByAdmissionIdAndIsCurrentTrueAndTenantId(admissionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Active Bed Allocation", "admissionId", admissionId));

        // If shifting to the same bed, do nothing
        if (activeAlloc.getBed().getId().equals(newBedId)) {
            return;
        }

        // 3. Get new bed
        Bed newBed = bedRepository.findById(newBedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed", "id", newBedId));

        if (newBed.getStatus() != BedStatus.AVAILABLE && newBed.getStatus() != BedStatus.CLEANING) {
            throw new InvalidStateTransitionException("Bed Allocation", newBed.getStatus().name(), "OCCUPIED");
        }

        // 4. Close active allocation
        activeAlloc.setIsCurrent(false);
        activeAlloc.setEndTime(LocalDateTime.now());
        activeAlloc.setTransferReason(transferReason != null && !transferReason.trim().isEmpty() ? transferReason.trim() : "Transferred to Bed " + newBed.getBedNumber());
        allocationRepository.save(activeAlloc);

        // Free old bed
        Bed oldBed = activeAlloc.getBed();
        oldBed.setStatus(BedStatus.AVAILABLE);
        bedRepository.save(oldBed);

        // 5. Occupy new bed
        newBed.setStatus(BedStatus.OCCUPIED);
        bedRepository.save(newBed);

        // 6. Create new allocation
        BedAllocation newAlloc = BedAllocation.builder()
                .admission(admission)
                .bed(newBed)
                .startTime(LocalDateTime.now())
                .isCurrent(true)
                .dailyPriceAtTime(catalogResolver.resolveBedCharge(newBed.getRoom().getRoomType(), tenantId).getPrice())
                .nursingChargeAtTime(catalogResolver.resolveOptional("NURSING_CHARGE", tenantId).map(HospitalService::getPrice).orElse(null))
                .dietChargeAtTime(catalogResolver.resolveOptional("DIET_CHARGE", tenantId).map(HospitalService::getPrice).orElse(null))
                .build();
        newAlloc.setTenantId(tenantId);
        allocationRepository.save(newAlloc);

        log.info("Transferred patient in Admission {} from Bed {} to Bed {} (Reason: {})", admissionId, oldBed.getBedNumber(), newBed.getBedNumber(), transferReason);
    }

    private AdmissionResponse mapToResponse(IpdAdmission admission, Bed currentBed) {
        AdmissionResponse.AdmissionResponseBuilder b = AdmissionResponse.builder()
                .id(admission.getId())
                .admissionNumber(admission.getAdmissionNumber())
                .patientId(admission.getPatient().getId())
                .patientName(admission.getPatient().getFirstName() + " " + admission.getPatient().getLastName())
                .patientCode(admission.getPatient().getPatientCode())
                .primaryDoctorId(admission.getPrimaryDoctor().getId())
                .primaryDoctorName(admission.getPrimaryDoctor().getUser().getFullName())
                .opdAppointmentId(admission.getOpdAppointment() != null ? admission.getOpdAppointment().getId() : null)
                .admissionType(admission.getAdmissionType())
                .status(admission.getStatus())
                .admissionTime(admission.getAdmissionTime())
                .expectedDischargeTime(admission.getExpectedDischargeTime())
                .depositAmount(admission.getDepositAmount())
                .preAuthId(admission.getPreAuthId());

        if (currentBed != null) {
            b.currentBedId(currentBed.getId())
             .currentBedNumber(currentBed.getBedNumber())
             .currentRoomNumber(currentBed.getRoom().getRoomNumber())
             .currentRoomType(currentBed.getRoom().getRoomType() != null ? currentBed.getRoom().getRoomType().name() : null)
             .currentWardName(currentBed.getRoom().getWard().getName());
        }

        return b.build();
    }
}
