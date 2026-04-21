package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.AddProgressNoteRequest;
import com.curamatrix.hsm.dto.request.AddVitalSignRequest;
import com.curamatrix.hsm.dto.response.DailyProgressNoteResponse;
import com.curamatrix.hsm.dto.response.VitalSignResponse;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClinicalCareService {

    private final IpdAdmissionRepository admissionRepository;
    private final VitalSignRepository vitalSignRepository;
    private final DailyProgressNoteRepository progressNoteRepository;
    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    // ─── VITALS ─────────────────────────────────────────────────────────────

    @Transactional
    public VitalSignResponse addVitalSign(Long admissionId, AddVitalSignRequest request) {
        Long tenantId = TenantContext.getTenantId();
        IpdAdmission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission", "id", admissionId));

        if (!admission.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Tenant mismatch");
        }

        User user = getCurrentUser();

        VitalSign vital = VitalSign.builder()
                .ipdAdmission(admission)
                .bpSystolic(request.getBpSystolic())
                .bpDiastolic(request.getBpDiastolic())
                .heartRate(request.getHeartRate())
                .temperature(request.getTemperature())
                .spO2(request.getSpO2())
                .respiratoryRate(request.getRespiratoryRate())
                .recordedBy(user)
                .build();
        vital.setTenantId(tenantId);
        
        VitalSign saved = vitalSignRepository.save(vital);
        return mapVitalSign(saved);
    }

    @Transactional(readOnly = true)
    public List<VitalSignResponse> getVitalSigns(Long admissionId) {
        Long tenantId = TenantContext.getTenantId();
        List<VitalSign> vitals = vitalSignRepository.findByIpdAdmissionIdAndTenantIdOrderByRecordedAtDesc(admissionId, tenantId);
        return vitals.stream().map(this::mapVitalSign).collect(Collectors.toList());
    }

    private VitalSignResponse mapVitalSign(VitalSign v) {
        return VitalSignResponse.builder()
                .id(v.getId())
                .bpSystolic(v.getBpSystolic())
                .bpDiastolic(v.getBpDiastolic())
                .heartRate(v.getHeartRate())
                .temperature(v.getTemperature())
                .spO2(v.getSpO2())
                .respiratoryRate(v.getRespiratoryRate())
                .recordedAt(v.getRecordedAt())
                .recordedByName(v.getRecordedBy().getFullName())
                .recordedByRole(v.getRecordedBy().getRoles().stream()
                        .findFirst()
                        .map(r -> r.getName().name())
                        .orElse("UNKNOWN"))
                .build();
    }

    // ─── PROGRESS NOTES ───────────────────────────────────────────────────────

    @Transactional
    public DailyProgressNoteResponse addProgressNote(Long admissionId, AddProgressNoteRequest request) {
        Long tenantId = TenantContext.getTenantId();
        IpdAdmission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission", "id", admissionId));

        User user = getCurrentUser();
        
        Doctor finalDoctor = doctorRepository.findByUserId(user.getId()).orElse(null);
        
        DailyProgressNote note = DailyProgressNote.builder()
                .ipdAdmission(admission)
                .subjective(request.getSubjective())
                .objective(request.getObjective())
                .assessment(request.getAssessment())
                .plan(request.getPlan())
                .doctor(finalDoctor)
                .build();
        note.setTenantId(tenantId);

        DailyProgressNote saved = progressNoteRepository.save(note);
        return mapProgressNote(saved);
    }

    @Transactional(readOnly = true)
    public List<DailyProgressNoteResponse> getProgressNotes(Long admissionId) {
        Long tenantId = TenantContext.getTenantId();
        List<DailyProgressNote> notes = progressNoteRepository.findByIpdAdmissionIdAndTenantIdOrderByNoteTimeDesc(admissionId, tenantId);
        return notes.stream().map(this::mapProgressNote).collect(Collectors.toList());
    }

    private DailyProgressNoteResponse mapProgressNote(DailyProgressNote n) {
        return DailyProgressNoteResponse.builder()
                .id(n.getId())
                .subjective(n.getSubjective())
                .objective(n.getObjective())
                .assessment(n.getAssessment())
                .plan(n.getPlan())
                .noteTime(n.getNoteTime())
                .doctorName(n.getDoctor() != null ? n.getDoctor().getUser().getFullName() : "Admin/Nurse Override")
                .build();
    }
}
