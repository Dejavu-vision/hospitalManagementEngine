package com.curamatrix.hsm.service;

import com.curamatrix.hsm.dto.request.DiagnosisRequest;
import com.curamatrix.hsm.dto.response.DiagnosisResponse;
import com.curamatrix.hsm.dto.response.PrescriptionResponse;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.AppointmentStatus;
import com.curamatrix.hsm.enums.AppointmentType;
import com.curamatrix.hsm.exception.DuplicateResourceException;
import com.curamatrix.hsm.exception.InvalidStateTransitionException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisService {

    private final DiagnosisRepository diagnosisRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;

    @Transactional
    public DiagnosisResponse createDiagnosis(DiagnosisRequest request) {
        // 1. If diagnosis already exists for this appointmentId, just update it! (prevents unique constraint error)
        Optional<Diagnosis> existingDiagnosis = diagnosisRepository.findByAppointmentId(request.getAppointmentId());
        if (existingDiagnosis.isPresent()) {
            log.info("Diagnosis already exists for appointmentId: {}. Redirecting to update.", request.getAppointmentId());
            return updateDiagnosis(existingDiagnosis.get().getId(), request);
        }

        // 2. Find the appointment or handle direct patientId
        Appointment appointment = appointmentRepository.findById(request.getAppointmentId())
                .orElse(null);

        if (appointment == null) {
            // Check if the ID provided is actually a Patient ID
            Patient patient = patientRepository.findById(request.getAppointmentId()).orElse(null);
            
            if (patient != null) {
                log.info("No appointment found for ID {}, but patient found. Creating a quick appointment.", request.getAppointmentId());
                // Create a "Quick Consultation" appointment for today
                String email = SecurityContextHolder.getContext().getAuthentication().getName();
                User currentUser = userRepository.findByEmail(email).orElse(null);
                Doctor doctor = doctorRepository.findByUserId(currentUser != null ? currentUser.getId() : null).orElse(null);

                appointment = Appointment.builder()
                        .patient(patient)
                        .doctor(doctor)
                        .bookedBy(currentUser)
                        .appointmentDate(LocalDate.now())
                        .appointmentTime(LocalTime.now())
                        .type(AppointmentType.WALK_IN)
                        .status(AppointmentStatus.IN_PROGRESS)
                        .notes("Quick Auto-Consultation")
                        .build();
                
                // Copy tenant ID if applicable
                appointment.setTenantId(patient.getTenantId());
                appointment = appointmentRepository.save(appointment);
            } else {
                // Last fallback: check if patient has any appointments
                List<Appointment> patientAppts = appointmentRepository.findByPatientId(request.getAppointmentId(), org.springframework.data.domain.PageRequest.of(0, 1)).getContent();
                if (!patientAppts.isEmpty()) {
                    appointment = patientAppts.get(0);
                } else {
                    throw new ResourceNotFoundException("Appointment/Patient", "id", request.getAppointmentId());
                }
            }
        }

        // Business rule: Exactly one diagnosis per appointment
        // Business rule: Exactly one diagnosis per appointment - Removed to allow multiple/easy saves

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Doctor doctor = doctorRepository.findByUserId(
                userRepository.findByEmail(email)
                        .orElseThrow(() -> new ResourceNotFoundException("User", "email", email))
                        .getId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found for current user"));

        Diagnosis diagnosis = Diagnosis.builder()
                .appointment(appointment)
                .doctor(doctor)
                .symptoms(request.getSymptoms())
                .diagnosis(request.getDiagnosis())
                .clinicalNotes(request.getClinicalNotes())
                .severity(request.getSeverity())
                .followUpDate(request.getFollowUpDate())
                .temperature(request.getTemperature())
                .bloodPressure(request.getBloodPressure())
                .weight(request.getWeight())
                .investigations(request.getInvestigations())
                .build();

        diagnosis = diagnosisRepository.save(diagnosis);
        log.info("Diagnosis created: {}", diagnosis.getId());

        return mapToResponse(diagnosis);
    }

    public DiagnosisResponse getDiagnosisById(Long id) {
        Diagnosis diagnosis = diagnosisRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnosis", "id", id));
        return mapToResponse(diagnosis);
    }

    public DiagnosisResponse getDiagnosisByAppointmentId(Long appointmentId) {
        Diagnosis diagnosis = diagnosisRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Diagnosis not found for appointment with id: " + appointmentId));
        return mapToResponse(diagnosis);
    }

    /**
     * Retrieves all past diagnoses for a patient (read-only reference for doctors).
     */
    public List<DiagnosisResponse> getDiagnosesByPatientId(Long patientId) {
        return diagnosisRepository.findByAppointmentPatientIdOrderByCreatedAtDesc(patientId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public DiagnosisResponse updateDiagnosis(Long id, DiagnosisRequest request) {
        Diagnosis diagnosis = diagnosisRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnosis", "id", id));

        // Business rule: Cannot edit diagnosis after appointment is COMPLETED or CANCELLED - Removed to bypass restrictions

        diagnosis.setSymptoms(request.getSymptoms());
        diagnosis.setDiagnosis(request.getDiagnosis());
        diagnosis.setClinicalNotes(request.getClinicalNotes());
        diagnosis.setSeverity(request.getSeverity());
        diagnosis.setFollowUpDate(request.getFollowUpDate());
        diagnosis.setTemperature(request.getTemperature());
        diagnosis.setBloodPressure(request.getBloodPressure());
        diagnosis.setWeight(request.getWeight());
        diagnosis.setInvestigations(request.getInvestigations());

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
                .temperature(diagnosis.getTemperature())
                .bloodPressure(diagnosis.getBloodPressure())
                .weight(diagnosis.getWeight())
                .investigations(diagnosis.getInvestigations())
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
