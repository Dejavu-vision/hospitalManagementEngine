package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.response.InsurancePolicyResponse;
import com.curamatrix.hsm.dto.response.PatientAllergyResponse;
import com.curamatrix.hsm.dto.response.PatientNoteResponse;
import com.curamatrix.hsm.dto.response.PatientProfileResponse;
import com.curamatrix.hsm.dto.response.PatientProfileResponse.*;
import com.curamatrix.hsm.dto.response.PatientVitalResponse;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.*;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Builds the unified, role-aware patient profile in one call.
 * Receptionists get demographics, case paper, visits and finance;
 * doctors additionally get clinical data (diagnoses, active medications)
 * and no financial detail. Admins see everything.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PatientProfileService {

    private static final int RECENT_VISITS = 5;
    private static final int MEDICATION_LOOKBACK_DAYS = 365;

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final BillingRepository billingRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final PatientRegistrationRepository registrationRepository;
    private final IpdAdmissionRepository admissionRepository;
    private final PatientAllergyRepository allergyRepository;
    private final PatientVitalRepository vitalRepository;
    private final PatientNoteRepository noteRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PatientProfileResponse getProfile(Long patientId) {
        Long tenantId = TenantContext.getTenantId();
        Patient patient = patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId));

        PatientViewRole role = PatientViewRole.current();
        boolean clinical = role.canSeeClinical();
        boolean financial = role.canSeeFinancial();
        LocalDate today = LocalDate.now();

        List<Appointment> appointments = new ArrayList<>(
                appointmentRepository.findAllByPatientIdAndTenantId(patientId, tenantId));
        appointments.sort(Comparator
                .comparing(Appointment::getAppointmentDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(a -> a.getAppointmentTime() != null ? a.getAppointmentTime() : java.time.LocalTime.MIN)
                .reversed());

        List<Diagnosis> diagnoses = diagnosisRepository.findByPatientIdAndTenantId(patientId, tenantId);
        List<PatientAllergyResponse> allergies = allergyRepository
                .findByPatientIdAndTenantIdAndActiveTrueOrderByRecordedAtDesc(patientId, tenantId)
                .stream().map(PatientClinicalService::toResponse).toList();
        PatientVitalResponse latestVitals = vitalRepository
                .findByPatientIdAndTenantIdOrderByRecordedAtDesc(patientId, tenantId, PageRequest.of(0, 1))
                .stream().findFirst().map(PatientClinicalService::toResponse).orElse(null);
        List<IpdAdmission> admissions = admissionRepository.findByPatientIdAndTenantId(patientId, tenantId);

        CasePaper casePaper = buildCasePaper(patientId, tenantId);
        Stats stats = buildStats(appointments, diagnoses, admissions, today);
        Financial fin = buildFinancial(patientId, tenantId);

        List<Alert> alerts = buildAlerts(patient, allergies, latestVitals, casePaper, stats, fin, financial, today);

        List<String> sections = new ArrayList<>(List.of(
                "OVERVIEW", "LAST_VISIT", "TIMELINE", "VISITS", "ALLERGIES", "VITALS", "NOTES", "LAB"));
        // Case papers are issued and printed through reception-only endpoints.
        if (role == PatientViewRole.RECEPTIONIST) { sections.add("CASE_PAPERS"); }
        if (clinical) { sections.add("CLINICAL_HISTORY"); sections.add("MEDICATIONS"); }
        if (financial) { sections.add("BILLING"); sections.add("INSURANCE"); }

        return PatientProfileResponse.builder()
                .viewRole(role.name())
                .sections(sections)
                .header(buildHeader(patient))
                .alerts(alerts)
                .demographics(buildDemographics(patient))
                .casePaper(casePaper)
                .activeVisit(buildActiveVisit(appointments, today))
                .stats(stats)
                .allergies(allergies)
                .latestVitals(latestVitals)
                .financial(financial ? fin : null)
                .insurance(financial ? buildInsurance(patient) : List.of())
                .recentVisits(buildRecentVisits(appointments, diagnoses, clinical))
                .activeMedications(clinical ? buildMedications(diagnoses, today) : List.of())
                .pinnedNotes(buildPinnedNotes(patientId, tenantId, role))
                .build();
    }

    // ───────────────────────── Sections ─────────────────────────

    private Header buildHeader(Patient p) {
        String fullName = (p.getFirstName() + " " + (p.getLastName() != null ? p.getLastName() : "")).trim();
        return Header.builder()
                .id(p.getId())
                .patientCode(p.getPatientCode())
                .fullName(fullName)
                .age(p.getDateOfBirth() != null ? Period.between(p.getDateOfBirth(), LocalDate.now()).getYears() : null)
                .gender(p.getGender() != null ? p.getGender().name() : null)
                .bloodGroup(p.getBloodGroup() != null ? p.getBloodGroup().name() : null)
                .phone(p.getPhone())
                .dateOfBirth(p.getDateOfBirth())
                .registeredAt(p.getRegisteredAt())
                .checkedIn(Boolean.TRUE.equals(p.getCheckedIn()) && !Boolean.TRUE.equals(p.getCheckedOut()))
                .build();
    }

    private Demographics buildDemographics(Patient p) {
        return Demographics.builder()
                .firstName(p.getFirstName())
                .lastName(p.getLastName())
                .email(p.getEmail())
                .address(p.getAddress())
                .guardianName(p.getGuardianName())
                .emergencyContactName(p.getEmergencyContactName())
                .emergencyContactPhone(p.getEmergencyContactPhone())
                .medicalHistory(p.getMedicalHistory())
                .legacyAllergies(p.getAllergies())
                .build();
    }

    private CasePaper buildCasePaper(Long patientId, Long tenantId) {
        Optional<PatientRegistration> opt = registrationRepository
                .findFirstByPatientIdAndTenantIdAndActiveTrueOrderByExpiresAtDesc(patientId, tenantId);
        if (opt.isEmpty()) {
            return CasePaper.builder().exists(false).valid(false).remainingDays(-1).build();
        }
        PatientRegistration reg = opt.get();
        boolean valid = !reg.isExpired();
        long remaining = valid ? ChronoUnit.DAYS.between(LocalDateTime.now(), reg.getExpiresAt()) : -1;
        return CasePaper.builder()
                .exists(true)
                .valid(valid)
                .issuedAt(reg.getIssuedAt())
                .expiresAt(reg.getExpiresAt())
                .remainingDays(remaining)
                .expiringSoon(valid && remaining <= 5)
                .build();
    }

    private ActiveVisit buildActiveVisit(List<Appointment> appointments, LocalDate today) {
        return appointments.stream()
                .filter(a -> today.equals(a.getAppointmentDate()))
                .filter(a -> a.getStatus() == AppointmentStatus.BOOKED
                        || a.getStatus() == AppointmentStatus.CHECKED_IN
                        || a.getStatus() == AppointmentStatus.IN_PROGRESS
                        || a.getStatus() == AppointmentStatus.ON_HOLD
                        || a.getStatus() == AppointmentStatus.RECALLED)
                .findFirst()
                .map(a -> ActiveVisit.builder()
                        .appointmentId(a.getId())
                        .status(a.getStatus().name())
                        .tokenNumber(a.getTokenNumber())
                        .doctorId(a.getDoctor() != null ? a.getDoctor().getId() : null)
                        .doctorName(doctorName(a))
                        .department(department(a))
                        .build())
                .orElse(null);
    }

    private Stats buildStats(List<Appointment> appointments, List<Diagnosis> diagnoses,
                             List<IpdAdmission> admissions, LocalDate today) {
        long completed = appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count();
        long noShows = appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.NO_SHOW).count();
        LocalDate lastVisit = appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED)
                .map(Appointment::getAppointmentDate).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        LocalDate nextFollowUp = diagnoses.stream()
                .map(Diagnosis::getFollowUpDate)
                .filter(d -> d != null && !d.isBefore(today))
                .min(Comparator.naturalOrder()).orElse(null);
        List<IpdAdmission> real = admissions.stream()
                .filter(a -> a.getStatus() != AdmissionStatus.CANCELLED).toList();
        return Stats.builder()
                .completedVisits(completed)
                .totalAppointments(appointments.size())
                .noShows(noShows)
                .lastVisitDate(lastVisit)
                .nextFollowUpDate(nextFollowUp)
                .admissions(real.size())
                .currentlyAdmitted(real.stream().anyMatch(a -> a.getStatus() == AdmissionStatus.ADMITTED))
                .build();
    }

    private Financial buildFinancial(Long patientId, Long tenantId) {
        BigDecimal billed = BigDecimal.ZERO, paid = BigDecimal.ZERO, outstanding = BigDecimal.ZERO;
        long pending = 0;
        for (Billing b : billingRepository.findAllByPatientIdAndTenantId(patientId, tenantId)) {
            if (b.getPaymentStatus() == PaymentStatus.CANCELLED) continue;
            BigDecimal net = b.getNetAmount() != null ? b.getNetAmount() : BigDecimal.ZERO;
            BigDecimal pd = b.getPaidAmount() != null ? b.getPaidAmount() : BigDecimal.ZERO;
            billed = billed.add(net);
            paid = paid.add(pd);
            BigDecimal balance = net.subtract(pd);
            if (b.getPaymentStatus() != PaymentStatus.PAID && balance.signum() > 0) {
                outstanding = outstanding.add(balance);
                pending++;
            }
        }
        return Financial.builder().totalBilled(billed).totalPaid(paid)
                .outstanding(outstanding).pendingInvoices(pending).build();
    }

    private List<InsurancePolicyResponse> buildInsurance(Patient p) {
        if (p.getInsurancePolicies() == null) return List.of();
        return p.getInsurancePolicies().stream().map(ip -> InsurancePolicyResponse.builder()
                .id(ip.getId())
                .payerId(ip.getPayer() != null ? ip.getPayer().getId() : null)
                .insurerName(ip.getPayer() != null ? ip.getPayer().getInsurerName() : null)
                .tpaName(ip.getPayer() != null ? ip.getPayer().getTpaName() : null)
                .policyNumber(ip.getPolicyNumber())
                .memberId(ip.getMemberId())
                .sumInsured(ip.getSumInsured())
                .roomRentLimit(ip.getRoomRentLimit())
                .copayPct(ip.getCopayPct())
                .validFrom(ip.getValidFrom())
                .validTo(ip.getValidTo())
                .policyType(ip.getPolicyType())
                .build()).toList();
    }

    private List<RecentVisit> buildRecentVisits(List<Appointment> appointments,
                                                List<Diagnosis> diagnoses, boolean clinical) {
        Map<Long, Diagnosis> byAppointment = new HashMap<>();
        if (clinical) {
            for (Diagnosis d : diagnoses) {
                byAppointment.put(d.getAppointment().getId(), d);
            }
        }
        return appointments.stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .limit(RECENT_VISITS)
                .map(a -> {
                    Diagnosis d = byAppointment.get(a.getId());
                    return RecentVisit.builder()
                            .appointmentId(a.getId())
                            .date(a.getAppointmentDate())
                            .type(a.getType() != null ? a.getType().name() : null)
                            .status(a.getStatus() != null ? a.getStatus().name() : null)
                            .tokenNumber(a.getTokenNumber())
                            .doctorName(doctorName(a))
                            .department(department(a))
                            .diagnosis(d != null ? d.getDiagnosis() : null)
                            .symptoms(d != null ? d.getSymptoms() : null)
                            .build();
                }).toList();
    }

    private List<ActiveMedication> buildMedications(List<Diagnosis> diagnoses, LocalDate today) {
        List<ActiveMedication> meds = new ArrayList<>();
        for (Diagnosis d : diagnoses) {
            if (d.getCreatedAt() == null) continue;
            LocalDate prescribedOn = d.getCreatedAt().toLocalDate();
            if (prescribedOn.isBefore(today.minusDays(MEDICATION_LOOKBACK_DAYS))) continue;
            for (Prescription rx : d.getPrescriptions()) {
                // Unknown duration: treat as ongoing only for the last 30 days
                LocalDate endsOn = rx.getDurationDays() != null
                        ? prescribedOn.plusDays(rx.getDurationDays()) : prescribedOn.plusDays(30);
                if (endsOn.isBefore(today)) continue;
                meds.add(ActiveMedication.builder()
                        .medicine(rx.getMedicine() != null ? rx.getMedicine().getName() : null)
                        .dosage(rx.getDosage())
                        .frequency(rx.getFrequency())
                        .durationDays(rx.getDurationDays())
                        .instructions(rx.getInstructions())
                        .prescribedOn(prescribedOn)
                        .endsOn(endsOn)
                        .prescribedBy(d.getDoctor() != null && d.getDoctor().getUser() != null
                                ? d.getDoctor().getUser().getFullName() : null)
                        .build());
            }
        }
        meds.sort(Comparator.comparing(ActiveMedication::getPrescribedOn).reversed());
        return meds;
    }

    private List<PatientNoteResponse> buildPinnedNotes(Long patientId, Long tenantId, PatientViewRole role) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        User me = auth != null ? userRepository.findByEmail(auth.getName()).orElse(null) : null;
        return noteRepository
                .findByPatientIdAndTenantIdAndNoteTypeInOrderByPinnedDescCreatedAtDesc(
                        patientId, tenantId, PatientClinicalService.visibleNoteTypes(role))
                .stream().filter(PatientNote::isPinned).limit(3)
                .map(n -> PatientClinicalService.toResponse(n, me, role)).toList();
    }

    private List<Alert> buildAlerts(Patient patient, List<PatientAllergyResponse> allergies,
                                    PatientVitalResponse vitals, CasePaper casePaper, Stats stats,
                                    Financial fin, boolean financial, LocalDate today) {
        List<Alert> alerts = new ArrayList<>();

        for (PatientAllergyResponse a : allergies) {
            boolean severe = a.getSeverity() == AllergySeverity.SEVERE
                    || a.getSeverity() == AllergySeverity.LIFE_THREATENING;
            alerts.add(Alert.builder()
                    .level(severe ? "CRITICAL" : "WARNING").type("ALLERGY")
                    .message("Allergy: " + a.getAllergen()
                            + (a.getReaction() != null ? " (" + a.getReaction() + ")" : "")
                            + " · " + a.getSeverity().name().replace('_', ' '))
                    .build());
        }
        if (allergies.isEmpty() && patient.getAllergies() != null && !patient.getAllergies().isBlank()) {
            alerts.add(Alert.builder().level("WARNING").type("ALLERGY")
                    .message("Allergies (unstructured): " + patient.getAllergies().trim()).build());
        }

        if (vitals != null) {
            if (vitals.getSpo2() != null && vitals.getSpo2() < 92) {
                alerts.add(Alert.builder().level("CRITICAL").type("VITALS")
                        .message("Low SpO2 " + vitals.getSpo2() + "% at last reading").build());
            }
            if (vitals.getBpSystolic() != null && (vitals.getBpSystolic() >= 160 || vitals.getBpSystolic() < 90)) {
                alerts.add(Alert.builder().level("WARNING").type("VITALS")
                        .message("Abnormal BP " + vitals.getBpSystolic() + "/" + vitals.getBpDiastolic()
                                + " at last reading").build());
            }
            if (vitals.getTemperatureC() != null && vitals.getTemperatureC().compareTo(new BigDecimal("38.5")) >= 0) {
                alerts.add(Alert.builder().level("WARNING").type("VITALS")
                        .message("Fever " + vitals.getTemperatureC() + " °C at last reading").build());
            }
        }

        if (stats.isCurrentlyAdmitted()) {
            alerts.add(Alert.builder().level("INFO").type("ADMISSION")
                    .message("Currently admitted (IPD)").build());
        }
        if (stats.getNextFollowUpDate() != null
                && !stats.getNextFollowUpDate().isAfter(today.plusDays(7))) {
            alerts.add(Alert.builder().level("INFO").type("FOLLOW_UP")
                    .message("Follow-up due " + stats.getNextFollowUpDate()).build());
        }

        if (financial) {
            if (!casePaper.isValid()) {
                alerts.add(Alert.builder().level("WARNING").type("CASE_PAPER")
                        .message(casePaper.isExists() ? "Case paper expired" : "No case paper issued").build());
            } else if (casePaper.isExpiringSoon()) {
                alerts.add(Alert.builder().level("INFO").type("CASE_PAPER")
                        .message("Case paper expires in " + casePaper.getRemainingDays() + " day(s)").build());
            }
            if (fin.getOutstanding().signum() > 0) {
                alerts.add(Alert.builder().level("WARNING").type("BALANCE")
                        .message("Outstanding balance ₹" + fin.getOutstanding().stripTrailingZeros().toPlainString()
                                + " across " + fin.getPendingInvoices() + " invoice(s)").build());
            }
        }
        return alerts;
    }

    private String doctorName(Appointment a) {
        return a.getDoctor() != null && a.getDoctor().getUser() != null
                ? a.getDoctor().getUser().getFullName() : null;
    }

    private String department(Appointment a) {
        return a.getDoctor() != null && a.getDoctor().getDepartment() != null
                ? a.getDoctor().getDepartment().getName() : null;
    }
}
