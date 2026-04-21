package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.AdmissionRequest;
import com.curamatrix.hsm.dto.response.AdmissionResponse;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.AdmissionStatus;
import com.curamatrix.hsm.enums.BedStatus;
import com.curamatrix.hsm.exception.DuplicateResourceException;
import com.curamatrix.hsm.exception.InvalidStateTransitionException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.*;
import com.curamatrix.hsm.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class IpdAdmissionService {

    private final IpdAdmissionRepository admissionRepository;
    private final BedAllocationRepository allocationRepository;
    private final BedRepository bedRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final BillingRepository billingRepository;

    @Transactional
    public AdmissionResponse admitPatient(AdmissionRequest request) {
        Long tenantId = TenantContext.getTenantId();
        
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

        // Create Admission
        String admNumber = "IPD-" + System.currentTimeMillis(); // basic uniqueness
        IpdAdmission admission = IpdAdmission.builder()
                .admissionNumber(admNumber)
                .patient(patient)
                .primaryDoctor(doctor)
                .opdAppointment(opd)
                .admissionType(request.getAdmissionType())
                .status(AdmissionStatus.ADMITTED)
                .admissionTime(LocalDateTime.now())
                .admissionNotes(request.getAdmissionNotes())
                .build();
        admission.setTenantId(tenantId);
        admission = admissionRepository.save(admission);

        // Allocate Bed
        bed.setStatus(BedStatus.OCCUPIED);
        bedRepository.save(bed);
        
        BedAllocation allocation = BedAllocation.builder()
                .admission(admission)
                .bed(bed)
                .startTime(LocalDateTime.now())
                .isCurrent(true)
                .dailyPriceAtTime(bed.getDailyPrice())
                .build();
        allocation.setTenantId(tenantId);
        allocationRepository.save(allocation);

        // ─── Create Running Bill ─────────────────────────────────────────────
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
        billingRepository.save(ipdBilling);
        // ─────────────────────────────────────────────────────────────────────
        
        // TODO: Handle Deposit Billing if request.getDepositAmount() > 0

        return mapToResponse(admission, bed);
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
    public List<Map<String, Object>> getActiveAdmissions() {
        Long tenantId = TenantContext.getTenantId();
        List<IpdAdmission> active = admissionRepository.findByStatusAndTenantId(AdmissionStatus.ADMITTED, tenantId);

        return active.stream().map(adm -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", adm.getId());
            row.put("admissionNumber", adm.getAdmissionNumber());
            row.put("patientId", adm.getPatient().getId());
            row.put("patientName", adm.getPatient().getFirstName() + " " + adm.getPatient().getLastName());
            row.put("primaryDoctorName", adm.getPrimaryDoctor().getUser().getFullName());
            row.put("admissionType", adm.getAdmissionType() != null ? adm.getAdmissionType().name() : null);
            row.put("admissionTime", adm.getAdmissionTime() != null ? adm.getAdmissionTime().toString() : null);

            long daysAdmitted = adm.getAdmissionTime() != null
                    ? ChronoUnit.DAYS.between(adm.getAdmissionTime().toLocalDate(), LocalDateTime.now().toLocalDate()) + 1
                    : 0;
            row.put("daysAdmitted", daysAdmitted);

            // Current bed info
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

            // Running bill total
            Billing bill = billingRepository.findByIpdAdmissionId(adm.getId()).orElse(null);
            row.put("runningBillTotal", bill != null ? bill.getNetAmount() : BigDecimal.ZERO);
            row.put("paidAmount", bill != null ? bill.getPaidAmount() : BigDecimal.ZERO);

            return row;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdmissionBilling(Long admissionId) {
        Long tenantId = TenantContext.getTenantId();
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

    private AdmissionResponse mapToResponse(IpdAdmission admission, Bed currentBed) {
        AdmissionResponse.AdmissionResponseBuilder b = AdmissionResponse.builder()
                .id(admission.getId())
                .admissionNumber(admission.getAdmissionNumber())
                .patientId(admission.getPatient().getId())
                .patientName(admission.getPatient().getFirstName() + " " + admission.getPatient().getLastName())
                .primaryDoctorId(admission.getPrimaryDoctor().getId())
                .primaryDoctorName(admission.getPrimaryDoctor().getUser().getFullName())
                .opdAppointmentId(admission.getOpdAppointment() != null ? admission.getOpdAppointment().getId() : null)
                .admissionType(admission.getAdmissionType())
                .status(admission.getStatus())
                .admissionTime(admission.getAdmissionTime());
                
        if (currentBed != null) {
            b.currentBedId(currentBed.getId())
             .currentBedNumber(currentBed.getBedNumber())
             .currentWardName(currentBed.getRoom().getWard().getName());
        }
        
        return b.build();
    }
}
