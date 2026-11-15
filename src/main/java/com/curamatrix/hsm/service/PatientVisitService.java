package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.response.PatientVisitDetailResponse;
import com.curamatrix.hsm.dto.response.PatientVitalResponse;
import com.curamatrix.hsm.dto.response.PatientVisitDetailResponse.*;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.AppointmentStatus;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Full record of a single visit: consultation, prescriptions, lab orders, vitals and bill. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PatientVisitService {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final LabPrescriptionRepository labPrescriptionRepository;
    private final PatientVitalRepository vitalRepository;
    private final BillingRepository billingRepository;

    /** Latest completed visit; falls back to the latest past visit that was not cancelled. */
    @Transactional(readOnly = true)
    public Optional<PatientVisitDetailResponse> getLastVisit(Long patientId) {
        Long tenantId = TenantContext.getTenantId();
        requirePatient(patientId, tenantId);
        List<Appointment> all = appointmentRepository.findAllByPatientIdAndTenantId(patientId, tenantId);
        Comparator<Appointment> byWhen = Comparator
                .comparing(Appointment::getAppointmentDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(a -> a.getAppointmentTime() != null ? a.getAppointmentTime() : java.time.LocalTime.MIN);
        LocalDate today = LocalDate.now();

        Optional<Appointment> pick = all.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED)
                .max(byWhen);
        if (pick.isEmpty()) {
            pick = all.stream()
                    .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED && a.getStatus() != AppointmentStatus.NO_SHOW)
                    .filter(a -> a.getAppointmentDate() != null && !a.getAppointmentDate().isAfter(today))
                    .max(byWhen);
        }
        return pick.map(a -> build(a, tenantId));
    }

    @Transactional(readOnly = true)
    public PatientVisitDetailResponse getVisit(Long patientId, Long appointmentId) {
        Long tenantId = TenantContext.getTenantId();
        requirePatient(patientId, tenantId);
        Appointment appt = appointmentRepository.findByIdAndTenantId(appointmentId, tenantId)
                .filter(a -> a.getPatient().getId().equals(patientId))
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));
        return build(appt, tenantId);
    }

    // ─────────────────────────────────────────────────────────────

    private PatientVisitDetailResponse build(Appointment appt, Long tenantId) {
        PatientViewRole role = PatientViewRole.current();
        boolean clinical = role.canSeeClinical();
        boolean financial = role.canSeeFinancial();
        Long patientId = appt.getPatient().getId();
        LocalDate visitDate = appt.getAppointmentDate();

        Diagnosis dx = diagnosisRepository.findByAppointmentId(appt.getId()).orElse(null);

        List<PatientVitalResponse> vitals = vitalRepository
                .findByPatientIdAndTenantIdAndAppointmentIdOrderByRecordedAtDesc(patientId, tenantId, appt.getId())
                .stream().map(PatientClinicalService::toResponse).toList();

        return PatientVisitDetailResponse.builder()
                .viewRole(role.name())
                .clinicalVisible(clinical)
                .visit(buildVisit(appt))
                .consultation(clinical && dx != null ? buildConsultation(dx) : null)
                .vitals(vitals)
                .prescriptions(clinical && dx != null ? buildPrescriptions(dx) : List.of())
                .labOrders(buildLabOrders(patientId, tenantId, appt, clinical))
                .bill(financial ? buildBill(appt, tenantId) : null)
                .followUp(dx != null ? buildFollowUp(dx) : null)
                .build();
    }

    private Visit buildVisit(Appointment a) {
        Long minutes = a.getConsultationStart() != null && a.getConsultationEnd() != null
                ? Duration.between(a.getConsultationStart(), a.getConsultationEnd()).toMinutes() : null;
        Doctor d = a.getDoctor();
        return Visit.builder()
                .appointmentId(a.getId())
                .date(a.getAppointmentDate())
                .time(a.getAppointmentTime())
                .type(a.getType() != null ? a.getType().name() : null)
                .status(a.getStatus() != null ? a.getStatus().name() : null)
                .tokenNumber(a.getTokenNumber())
                .doctorName(d != null && d.getUser() != null ? d.getUser().getFullName() : null)
                .department(d != null && d.getDepartment() != null ? d.getDepartment().getName() : null)
                .doctorQualification(d != null ? d.getQualification() : null)
                .checkedInAt(a.getCheckedInAt())
                .consultationStart(a.getConsultationStart())
                .consultationEnd(a.getConsultationEnd())
                .durationMinutes(minutes)
                .bookingNotes(a.getNotes())
                .build();
    }

    private Consultation buildConsultation(Diagnosis dx) {
        return Consultation.builder()
                .symptoms(dx.getSymptoms())
                .diagnosis(dx.getDiagnosis())
                .severity(dx.getSeverity() != null ? dx.getSeverity().name() : null)
                .clinicalNotes(dx.getClinicalNotes())
                .investigationsAdvised(dx.getInvestigations())
                .temperature(dx.getTemperature())
                .bloodPressure(dx.getBloodPressure())
                .weight(dx.getWeight())
                .doctorName(dx.getDoctor() != null && dx.getDoctor().getUser() != null
                        ? dx.getDoctor().getUser().getFullName() : null)
                .recordedAt(dx.getCreatedAt())
                .build();
    }

    private List<Medication> buildPrescriptions(Diagnosis dx) {
        List<Medication> out = new ArrayList<>();
        for (Prescription rx : dx.getPrescriptions()) {
            Medicine m = rx.getMedicine();
            out.add(Medication.builder()
                    .medicine(m != null ? m.getName() : null)
                    .genericName(m != null ? m.getGenericName() : null)
                    .strength(m != null ? m.getStrength() : null)
                    .form(m != null ? m.getForm() : null)
                    .dosage(rx.getDosage())
                    .frequency(rx.getFrequency())
                    .durationDays(rx.getDurationDays())
                    .instructions(rx.getInstructions())
                    .build());
        }
        return out;
    }

    private List<LabOrder> buildLabOrders(Long patientId, Long tenantId, Appointment appt, boolean clinical) {
        List<LabOrder> out = new ArrayList<>();
        for (LabPrescription lp : labPrescriptionRepository
                .findByPatientIdAndTenantIdOrderByCreatedAtDesc(patientId, tenantId)) {
            boolean linked = lp.getAppointment() != null && lp.getAppointment().getId().equals(appt.getId());
            boolean sameDayFrontDesk = lp.getAppointment() == null && lp.getCreatedAt() != null
                    && lp.getCreatedAt().toLocalDate().equals(appt.getAppointmentDate());
            if (!linked && !sameDayFrontDesk) continue;

            List<LabTestItem> tests = new ArrayList<>();
            for (LabTest t : lp.getLabTests()) {
                tests.add(LabTestItem.builder()
                        .id(t.getId())
                        .testName(t.getLabService() != null ? t.getLabService().getServiceName() : null)
                        .category(t.getLabService() != null && t.getLabService().getCategory() != null
                                ? t.getLabService().getCategory().name() : null)
                        .status(t.getStatus() != null ? t.getStatus().name() : null)
                        .testDate(t.getTestDate())
                        .completedAt(t.getCompletedAt())
                        .notes(clinical ? t.getNotes() : null)
                        .cancellationReason(t.getCancellationReason())
                        .results(clinical ? t.getResults().stream().map(this::toResult).toList() : List.of())
                        .documents(clinical ? t.getDocuments().stream().map(doc -> LabDocumentItem.builder()
                                .id(doc.getId()).fileName(doc.getFileName()).fileType(doc.getFileType()).build()).toList()
                                : List.of())
                        .build());
            }
            String orderedBy = lp.getDoctor() != null && lp.getDoctor().getUser() != null
                    ? "Dr. " + lp.getDoctor().getUser().getFullName()
                    : lp.getCreatedBy() != null ? lp.getCreatedBy().getFullName() : null;
            out.add(LabOrder.builder()
                    .labPrescriptionId(lp.getId())
                    .orderedBy(orderedBy)
                    .orderedAt(lp.getCreatedAt())
                    .linkedToVisit(linked)
                    .tests(tests)
                    .build());
        }
        return out;
    }

    private LabResultItem toResult(LabResult r) {
        return LabResultItem.builder()
                .parameter(r.getParameterName())
                .value(r.getResultValue())
                .unit(r.getUnit())
                .rangeLow(r.getNormalRangeLow())
                .rangeHigh(r.getNormalRangeHigh())
                .flag(flag(r.getResultValue(), r.getNormalRangeLow(), r.getNormalRangeHigh()))
                .observations(r.getObservations())
                .build();
    }

    /** HIGH/LOW/NORMAL when value and at least one bound are numeric, otherwise null. */
    static String flag(String value, String low, String high) {
        Double v = num(value), lo = num(low), hi = num(high);
        if (v == null || (lo == null && hi == null)) return null;
        if (lo != null && v < lo) return "LOW";
        if (hi != null && v > hi) return "HIGH";
        return "NORMAL";
    }

    private static Double num(String s) {
        if (s == null) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private Bill buildBill(Appointment appt, Long tenantId) {
        return billingRepository.findByAppointmentIdAndTenantId(appt.getId(), tenantId).map(b -> {
            BigDecimal net = b.getNetAmount() != null ? b.getNetAmount() : BigDecimal.ZERO;
            BigDecimal paid = b.getPaidAmount() != null ? b.getPaidAmount() : BigDecimal.ZERO;
            return Bill.builder()
                    .billingId(b.getId())
                    .invoiceNumber(b.getInvoiceNumber())
                    .paymentStatus(b.getPaymentStatus() != null ? b.getPaymentStatus().name() : null)
                    .total(b.getTotalAmount())
                    .discount(b.getDiscount())
                    .net(net)
                    .paid(paid)
                    .balance(net.subtract(paid).max(BigDecimal.ZERO))
                    .items(b.getItems().stream().map(i -> BillItem.builder()
                            .description(i.getDescription())
                            .type(i.getItemType() != null ? i.getItemType().name() : null)
                            .quantity(i.getQuantity())
                            .amount(i.getAmount())
                            .build()).toList())
                    .build();
        }).orElse(null);
    }

    private FollowUp buildFollowUp(Diagnosis dx) {
        if (dx.getFollowUpDate() == null) return null;
        return FollowUp.builder()
                .date(dx.getFollowUpDate())
                .daysFromToday(ChronoUnit.DAYS.between(LocalDate.now(), dx.getFollowUpDate()))
                .build();
    }

    private void requirePatient(Long patientId, Long tenantId) {
        patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId));
    }
}
