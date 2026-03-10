package com.curamatrix.hsm.service;

import com.curamatrix.hsm.dto.request.DiagnosisRequest;
import com.curamatrix.hsm.dto.response.DiagnosisResponse;
import com.curamatrix.hsm.dto.response.PrescriptionResponse;
import com.curamatrix.hsm.entity.Appointment;
import com.curamatrix.hsm.entity.Diagnosis;
import com.curamatrix.hsm.entity.Doctor;
import com.curamatrix.hsm.repository.AppointmentRepository;
import com.curamatrix.hsm.repository.DiagnosisRepository;
import com.curamatrix.hsm.repository.DoctorRepository;
import com.curamatrix.hsm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisService {

    private final DiagnosisRepository diagnosisRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;

    @Transactional
    public DiagnosisResponse createDiagnosis(DiagnosisRequest request) {
        Appointment appointment = appointmentRepository.findById(request.getAppointmentId())
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Doctor doctor = doctorRepository.findByUserId(
                userRepository.findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("User not found"))
                        .getId())
                .orElseThrow(() -> new RuntimeException("Doctor profile not found"));

        Diagnosis diagnosis = Diagnosis.builder()
                .appointment(appointment)
                .doctor(doctor)
                .symptoms(request.getSymptoms())
                .diagnosis(request.getDiagnosis())
                .clinicalNotes(request.getClinicalNotes())
                .severity(request.getSeverity())
                .followUpDate(request.getFollowUpDate())
                .build();

        diagnosis = diagnosisRepository.save(diagnosis);
        log.info("Diagnosis created: {}", diagnosis.getId());

        return mapToResponse(diagnosis);
    }

    public DiagnosisResponse getDiagnosisById(Long id) {
        Diagnosis diagnosis = diagnosisRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Diagnosis not found"));
        return mapToResponse(diagnosis);
    }

    public DiagnosisResponse getDiagnosisByAppointmentId(Long appointmentId) {
        Diagnosis diagnosis = diagnosisRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new RuntimeException("Diagnosis not found for appointment"));
        return mapToResponse(diagnosis);
    }

    @Transactional
    public DiagnosisResponse updateDiagnosis(Long id, DiagnosisRequest request) {
        Diagnosis diagnosis = diagnosisRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Diagnosis not found"));

        diagnosis.setSymptoms(request.getSymptoms());
        diagnosis.setDiagnosis(request.getDiagnosis());
        diagnosis.setClinicalNotes(request.getClinicalNotes());
        diagnosis.setSeverity(request.getSeverity());
        diagnosis.setFollowUpDate(request.getFollowUpDate());

        diagnosis = diagnosisRepository.save(diagnosis);
        log.info("Diagnosis updated: {}", id);

        return mapToResponse(diagnosis);
    }

    private DiagnosisResponse mapToResponse(Diagnosis diagnosis) {
        return DiagnosisResponse.builder()
                .id(diagnosis.getId())
                .appointmentId(diagnosis.getAppointment().getId())
                .doctorId(diagnosis.getDoctor().getId())
                .doctorName(diagnosis.getDoctor().getUser().getFullName())
                .patientId(diagnosis.getAppointment().getPatient().getId())
                .patientName(diagnosis.getAppointment().getPatient().getFirstName() + " " +
                        diagnosis.getAppointment().getPatient().getLastName())
                .symptoms(diagnosis.getSymptoms())
                .diagnosis(diagnosis.getDiagnosis())
                .clinicalNotes(diagnosis.getClinicalNotes())
                .severity(diagnosis.getSeverity())
                .followUpDate(diagnosis.getFollowUpDate())
                .prescriptions(diagnosis.getPrescriptions().stream()
                        .map(p -> PrescriptionResponse.builder()
                                .id(p.getId())
                                .medicineId(p.getMedicine().getId())
                                .medicineName(p.getMedicine().getName())
                                .medicineStrength(p.getMedicine().getStrength())
                                .medicineForm(p.getMedicine().getForm())
                                .dosage(p.getDosage())
                                .frequency(p.getFrequency())
                                .durationDays(p.getDurationDays())
                                .instructions(p.getInstructions())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(diagnosis.getCreatedAt())
                .build();
    }
}
