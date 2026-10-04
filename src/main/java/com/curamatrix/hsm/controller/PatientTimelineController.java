package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/patients/{patientId}/timeline")
@RequiredArgsConstructor
public class PatientTimelineController {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final BillingRepository billingRepository;
    private final PatientRegistrationRepository patientRegistrationRepository;
    private final IpdAdmissionRepository ipdAdmissionRepository;
    private final DiagnosisRepository diagnosisRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<?> getTimeline(@PathVariable Long patientId) {
        Long tenantId = TenantContext.getTenantId();

        patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        List<Map<String, Object>> events = new ArrayList<>();

        // 1. Appointments
        try {
            List<Appointment> appts = appointmentRepository.findAllByPatientIdAndTenantId(patientId, tenantId);
            for (Appointment a : appts) {
                Map<String, Object> e = new HashMap<>();
                e.put("type", "APPOINTMENT");
                e.put("date", a.getAppointmentDate() != null ? a.getAppointmentDate().toString() : null);
                e.put("timestamp", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
                e.put("doctorName", a.getDoctor() != null && a.getDoctor().getUser() != null ? a.getDoctor().getUser().getFullName() : null);
                e.put("department", a.getDoctor() != null && a.getDoctor().getDepartment() != null ? a.getDoctor().getDepartment().getName() : null);
                e.put("tokenNumber", a.getTokenNumber());
                e.put("status", a.getStatus() != null ? a.getStatus().name() : null);
                e.put("visitType", a.getType() != null ? a.getType().name() : null);
                events.add(e);
            }
        } catch (Exception ex) {
            log.warn("Failed to load appointments for timeline: {}", ex.getMessage());
        }

        // 2. IPD Admissions
        try {
            List<IpdAdmission> admissions = ipdAdmissionRepository.findByPatientIdAndTenantId(patientId, tenantId);
            for (IpdAdmission adm : admissions) {
                Map<String, Object> e = new HashMap<>();
                e.put("type", "ADMISSION");
                e.put("timestamp", adm.getAdmissionTime() != null ? adm.getAdmissionTime().toString() : null);
                e.put("admissionNumber", adm.getAdmissionNumber());
                e.put("status", adm.getStatus() != null ? adm.getStatus().name() : null);
                e.put("admittedAt", adm.getAdmissionTime() != null ? adm.getAdmissionTime().toString() : null);
                e.put("dischargedAt", adm.getActualDischargeTime() != null ? adm.getActualDischargeTime().toString() : null);
                e.put("doctorName", adm.getPrimaryDoctor() != null && adm.getPrimaryDoctor().getUser() != null ? adm.getPrimaryDoctor().getUser().getFullName() : null);
                e.put("department", adm.getPrimaryDoctor() != null && adm.getPrimaryDoctor().getDepartment() != null ? adm.getPrimaryDoctor().getDepartment().getName() : null);
                e.put("admissionType", adm.getAdmissionType() != null ? adm.getAdmissionType().name() : null);
                events.add(e);
            }
        } catch (Exception ex) {
            log.warn("Failed to load admissions for timeline: {}", ex.getMessage());
        }

        // 3. Billings
        try {
            List<Billing> bills = billingRepository.findAllByPatientIdAndTenantId(patientId, tenantId);
            for (Billing b : bills) {
                Map<String, Object> e = new HashMap<>();
                e.put("type", "BILLING");
                e.put("timestamp", b.getCreatedAt() != null ? b.getCreatedAt().toString() : null);
                e.put("invoiceNumber", b.getInvoiceNumber());
                e.put("totalAmount", b.getTotalAmount());
                e.put("paidAmount", b.getPaidAmount());
                e.put("netAmount", b.getNetAmount());
                e.put("paymentStatus", b.getPaymentStatus() != null ? b.getPaymentStatus().name() : null);
                e.put("paymentMethod", b.getPaymentMethod() != null ? b.getPaymentMethod().name() : null);
                e.put("paidAt", b.getPaidAt() != null ? b.getPaidAt().toString() : null);
                events.add(e);
            }
        } catch (Exception ex) {
            log.warn("Failed to load billings for timeline: {}", ex.getMessage());
        }

        // 4. Case Papers
        try {
            List<PatientRegistration> regs = patientRegistrationRepository.findByPatientIdAndTenantIdOrderByIssuedAtDesc(patientId, tenantId);
            for (PatientRegistration r : regs) {
                Map<String, Object> e = new HashMap<>();
                e.put("type", "CASE_PAPER");
                e.put("timestamp", r.getIssuedAt() != null ? r.getIssuedAt().toString() : null);
                e.put("issuedAt", r.getIssuedAt() != null ? r.getIssuedAt().toString() : null);
                e.put("expiresAt", r.getExpiresAt() != null ? r.getExpiresAt().toString() : null);
                e.put("active", r.isActive());
                e.put("expired", r.isExpired());
                events.add(e);
            }
        } catch (Exception ex) {
            log.warn("Failed to load case papers for timeline: {}", ex.getMessage());
        }

        // 5. Diagnoses
        try {
            List<Diagnosis> diagnoses = diagnosisRepository.findByPatientIdAndTenantId(patientId, tenantId);
            for (Diagnosis d : diagnoses) {
                Map<String, Object> e = new HashMap<>();
                e.put("type", "DIAGNOSIS");
                e.put("timestamp", d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
                e.put("doctorName", d.getDoctor() != null && d.getDoctor().getUser() != null ? d.getDoctor().getUser().getFullName() : null);
                e.put("summary", d.getClinicalNotes());
                e.put("chiefComplaint", d.getSymptoms());
                events.add(e);
            }
        } catch (Exception ex) {
            log.warn("Failed to load diagnoses for timeline: {}", ex.getMessage());
        }

        // Sort by timestamp descending (newest first)
        events.sort((a, b) -> {
            String ta = (String) a.get("timestamp");
            String tb = (String) b.get("timestamp");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });

        return ResponseEntity.ok(events);
    }
}
