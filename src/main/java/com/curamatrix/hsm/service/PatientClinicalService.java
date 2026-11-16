package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.PatientAllergyRequest;
import com.curamatrix.hsm.dto.request.PatientNoteRequest;
import com.curamatrix.hsm.dto.request.PatientVitalRequest;
import com.curamatrix.hsm.dto.response.PatientAllergyResponse;
import com.curamatrix.hsm.dto.response.PatientNoteResponse;
import com.curamatrix.hsm.dto.response.PatientVitalResponse;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.PatientNoteType;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/** Structured allergies, vitals and notes attached to a patient. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PatientClinicalService {

    private final PatientRepository patientRepository;
    private final PatientAllergyRepository allergyRepository;
    private final PatientVitalRepository vitalRepository;
    private final PatientNoteRepository noteRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;

    // ───────────────────────── Allergies ─────────────────────────

    @Transactional(readOnly = true)
    public List<PatientAllergyResponse> listAllergies(Long patientId) {
        Long tenantId = TenantContext.getTenantId();
        requirePatient(patientId, tenantId);
        return allergyRepository
                .findByPatientIdAndTenantIdAndActiveTrueOrderByRecordedAtDesc(patientId, tenantId)
                .stream().map(PatientClinicalService::toResponse).toList();
    }

    @Transactional
    public PatientAllergyResponse addAllergy(Long patientId, PatientAllergyRequest req) {
        Long tenantId = TenantContext.getTenantId();
        Patient patient = requirePatient(patientId, tenantId);
        PatientAllergy allergy = PatientAllergy.builder()
                .patient(patient)
                .allergen(req.getAllergen().trim())
                .reaction(blankToNull(req.getReaction()))
                .severity(req.getSeverity())
                .recordedBy(currentUser())
                .build();
        allergy.setTenantId(tenantId);
        return toResponse(allergyRepository.save(allergy));
    }

    @Transactional
    public void removeAllergy(Long patientId, Long allergyId) {
        Long tenantId = TenantContext.getTenantId();
        PatientAllergy allergy = allergyRepository
                .findByIdAndPatientIdAndTenantId(allergyId, patientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Allergy", "id", allergyId));
        allergy.setActive(false);
        allergyRepository.save(allergy);
    }

    // ───────────────────────── Vitals ─────────────────────────

    @Transactional(readOnly = true)
    public List<PatientVitalResponse> listVitals(Long patientId, int limit) {
        Long tenantId = TenantContext.getTenantId();
        requirePatient(patientId, tenantId);
        int size = Math.min(Math.max(limit, 1), 100);
        return vitalRepository
                .findByPatientIdAndTenantIdOrderByRecordedAtDesc(patientId, tenantId, PageRequest.of(0, size))
                .stream().map(PatientClinicalService::toResponse).toList();
    }

    @Transactional
    public PatientVitalResponse addVitals(Long patientId, PatientVitalRequest req) {
        Long tenantId = TenantContext.getTenantId();
        Patient patient = requirePatient(patientId, tenantId);

        boolean anyValue = req.getTemperatureC() != null || req.getBpSystolic() != null
                || req.getBpDiastolic() != null || req.getPulse() != null || req.getSpo2() != null
                || req.getRespiratoryRate() != null || req.getWeightKg() != null
                || req.getHeightCm() != null || req.getBloodSugar() != null;
        if (!anyValue) {
            throw new IllegalArgumentException("At least one vital sign value is required");
        }
        if ((req.getBpSystolic() == null) != (req.getBpDiastolic() == null)) {
            throw new IllegalArgumentException("Blood pressure needs both systolic and diastolic values");
        }
        if (req.getBpSystolic() != null && req.getBpSystolic() <= req.getBpDiastolic()) {
            throw new IllegalArgumentException("Systolic pressure must be higher than diastolic");
        }
        if (req.getAppointmentId() != null) {
            Appointment appt = appointmentRepository.findByIdAndTenantId(req.getAppointmentId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", req.getAppointmentId()));
            if (!appt.getPatient().getId().equals(patientId)) {
                throw new IllegalArgumentException("Appointment does not belong to this patient");
            }
        }

        PatientVital vital = PatientVital.builder()
                .patient(patient)
                .appointmentId(req.getAppointmentId())
                .temperatureC(req.getTemperatureC())
                .bpSystolic(req.getBpSystolic())
                .bpDiastolic(req.getBpDiastolic())
                .pulse(req.getPulse())
                .spo2(req.getSpo2())
                .respiratoryRate(req.getRespiratoryRate())
                .weightKg(req.getWeightKg())
                .heightCm(req.getHeightCm())
                .bloodSugar(req.getBloodSugar())
                .recordedBy(currentUser())
                .recordedAt(LocalDateTime.now())
                .build();
        vital.setTenantId(tenantId);
        return toResponse(vitalRepository.save(vital));
    }

    // ───────────────────────── Notes ─────────────────────────

    @Transactional(readOnly = true)
    public List<PatientNoteResponse> listNotes(Long patientId) {
        Long tenantId = TenantContext.getTenantId();
        requirePatient(patientId, tenantId);
        PatientViewRole role = PatientViewRole.current();
        User me = currentUser();
        return noteRepository
                .findByPatientIdAndTenantIdAndNoteTypeInOrderByPinnedDescCreatedAtDesc(
                        patientId, tenantId, visibleNoteTypes(role))
                .stream().map(n -> toResponse(n, me, role)).toList();
    }

    @Transactional
    public PatientNoteResponse addNote(Long patientId, PatientNoteRequest req) {
        Long tenantId = TenantContext.getTenantId();
        Patient patient = requirePatient(patientId, tenantId);
        PatientViewRole role = PatientViewRole.current();
        if (!visibleNoteTypes(role).contains(req.getNoteType())) {
            throw new AccessDeniedException("You cannot create " + req.getNoteType() + " notes");
        }
        User me = currentUser();
        PatientNote note = PatientNote.builder()
                .patient(patient)
                .noteType(req.getNoteType())
                .content(req.getContent().trim())
                .pinned(req.isPinned())
                .createdBy(me)
                .build();
        note.setTenantId(tenantId);
        return toResponse(noteRepository.save(note), me, role);
    }

    @Transactional
    public void deleteNote(Long patientId, Long noteId) {
        Long tenantId = TenantContext.getTenantId();
        PatientNote note = noteRepository.findByIdAndPatientIdAndTenantId(noteId, patientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Note", "id", noteId));
        PatientViewRole role = PatientViewRole.current();
        User me = currentUser();
        if (!visibleNoteTypes(role).contains(note.getNoteType()) || !canDelete(note, me, role)) {
            throw new AccessDeniedException("You cannot delete this note");
        }
        noteRepository.delete(note);
    }

    // ───────────────────────── Helpers ─────────────────────────

    private Patient requirePatient(Long patientId, Long tenantId) {
        return patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId));
    }

    private User currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    static EnumSet<PatientNoteType> visibleNoteTypes(PatientViewRole role) {
        return role.canSeeClinical()
                ? EnumSet.allOf(PatientNoteType.class)
                : EnumSet.of(PatientNoteType.FRONT_DESK);
    }

    private static boolean canDelete(PatientNote n, User me, PatientViewRole role) {
        if (role == PatientViewRole.ADMIN) return true;
        return me != null && n.getCreatedBy() != null && n.getCreatedBy().getId().equals(me.getId());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static PatientAllergyResponse toResponse(PatientAllergy a) {
        return PatientAllergyResponse.builder()
                .id(a.getId())
                .allergen(a.getAllergen())
                .reaction(a.getReaction())
                .severity(a.getSeverity())
                .recordedBy(a.getRecordedBy() != null ? a.getRecordedBy().getFullName() : null)
                .recordedAt(a.getRecordedAt())
                .build();
    }

    static PatientVitalResponse toResponse(PatientVital v) {
        BigDecimal bmi = null;
        if (v.getWeightKg() != null && v.getHeightCm() != null && v.getHeightCm().signum() > 0) {
            BigDecimal m = v.getHeightCm().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            bmi = v.getWeightKg().divide(m.multiply(m), 1, RoundingMode.HALF_UP);
        }
        return PatientVitalResponse.builder()
                .id(v.getId())
                .appointmentId(v.getAppointmentId())
                .temperatureC(v.getTemperatureC())
                .bpSystolic(v.getBpSystolic())
                .bpDiastolic(v.getBpDiastolic())
                .pulse(v.getPulse())
                .spo2(v.getSpo2())
                .respiratoryRate(v.getRespiratoryRate())
                .weightKg(v.getWeightKg())
                .heightCm(v.getHeightCm())
                .bmi(bmi)
                .bloodSugar(v.getBloodSugar())
                .recordedBy(v.getRecordedBy() != null ? v.getRecordedBy().getFullName() : null)
                .recordedAt(v.getRecordedAt())
                .build();
    }

    static PatientNoteResponse toResponse(PatientNote n, User me, PatientViewRole role) {
        return PatientNoteResponse.builder()
                .id(n.getId())
                .noteType(n.getNoteType())
                .content(n.getContent())
                .pinned(n.isPinned())
                .createdBy(n.getCreatedBy() != null ? n.getCreatedBy().getFullName() : null)
                .createdAt(n.getCreatedAt())
                .canDelete(canDelete(n, me, role))
                .build();
    }
}
