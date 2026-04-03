package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.ConsultationSubmitRequest;
import com.curamatrix.hsm.dto.request.DoctorStatusUpdateRequest;
import com.curamatrix.hsm.dto.response.*;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.AppointmentStatus;
import com.curamatrix.hsm.enums.DoctorStatus;
import com.curamatrix.hsm.exception.InvalidStateTransitionException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationService {

    private final AppointmentRepository appointmentRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final MedicineRepository medicineRepository;
    private final DoctorAvailabilityService doctorAvailabilityService;

    /**
     * Confirms patient arrival: validates CHECKED_IN status, transitions to IN_PROGRESS,
     * sets consultationStart, and updates doctor status to IN_CONSULTATION.
     */
    @Transactional
    public AppointmentResponse confirmArrival(Long appointmentId) {
        Long tenantId = TenantContext.getTenantId();
        Doctor doctor = getAuthenticatedDoctor();

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));

        // Tenant isolation
        if (!appointment.getTenantId().equals(tenantId)) {
            throw new AccessDeniedException("Access denied");
        }

        // Doctor-assignment validation
        if (!appointment.getDoctor().getId().equals(doctor.getId())) {
            throw new AccessDeniedException("Access denied. Appointment is not assigned to you.");
        }

        // Status validation
        if (appointment.getStatus() != AppointmentStatus.CHECKED_IN) {
            throw new InvalidStateTransitionException(
                    "Cannot confirm arrival. Appointment status is " + appointment.getStatus().name() +
                    ", expected CHECKED_IN.");
        }

        // Transition to IN_PROGRESS
        appointment.setStatus(AppointmentStatus.IN_PROGRESS);
        appointment.setConsultationStart(LocalDateTime.now());
        appointment = appointmentRepository.save(appointment);

        // Update doctor status to IN_CONSULTATION
        updateDoctorStatus(doctor.getId(), DoctorStatus.IN_CONSULTATION);

        log.info("Arrival confirmed for appointment {} by doctor {}", appointmentId, doctor.getId());
        return mapToAppointmentResponse(appointment);
    }

    /**
     * Atomic consultation submission: creates/updates Diagnosis, creates Prescriptions,
     * transitions appointment to COMPLETED, sets consultationEnd, and conditionally
     * resets doctor status to ON_DUTY.
     */
    @Transactional
    public ConsultationResponse submitConsultation(ConsultationSubmitRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Doctor doctor = getAuthenticatedDoctor();

        Appointment appointment = appointmentRepository.findById(request.getAppointmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", request.getAppointmentId()));

        // Tenant isolation
        if (!appointment.getTenantId().equals(tenantId)) {
            throw new AccessDeniedException("Access denied");
        }

        // Doctor-assignment validation
        if (!appointment.getDoctor().getId().equals(doctor.getId())) {
            throw new AccessDeniedException("Access denied. Appointment is not assigned to you.");
        }

        // Status validation
        if (appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new InvalidStateTransitionException(
                    "Cannot submit consultation. Appointment status is " + appointment.getStatus().name() +
                    ", expected IN_PROGRESS.");
        }

        // Create or update Diagnosis (upsert — exactly one per appointment)
        Diagnosis diagnosis;
        boolean existingDiagnosis = diagnosisRepository.existsByAppointmentId(request.getAppointmentId());
        if (existingDiagnosis) {
            diagnosis = diagnosisRepository.findByAppointmentId(request.getAppointmentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Diagnosis not found for appointment with id: " + request.getAppointmentId()));
            diagnosis.setSymptoms(request.getSymptoms());
            diagnosis.setDiagnosis(request.getDiagnosis());
            diagnosis.setClinicalNotes(request.getClinicalNotes());
            diagnosis.setSeverity(request.getSeverity());
            diagnosis.setFollowUpDate(request.getFollowUpDate());
            diagnosis.setTemperature(request.getTemperature());
            diagnosis.setBloodPressure(request.getBloodPressure());
            diagnosis.setWeight(request.getWeight());
            diagnosis.setInvestigations(request.getInvestigations());
            // Clear existing prescriptions for re-creation
            diagnosis.getPrescriptions().clear();
        } else {
            diagnosis = Diagnosis.builder()
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
        }
        diagnosis = diagnosisRepository.save(diagnosis);

        // Create Prescriptions if provided
        List<Prescription> prescriptions = Collections.emptyList();
        if (request.getPrescriptions() != null && !request.getPrescriptions().isEmpty()) {
            Diagnosis savedDiagnosis = diagnosis;
            prescriptions = request.getPrescriptions().stream()
                    .map(item -> {
                        Medicine medicine = medicineRepository.findById(item.getMedicineId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                        "Medicine", "id", item.getMedicineId()));
                        return Prescription.builder()
                                .diagnosis(savedDiagnosis)
                                .medicine(medicine)
                                .dosage(item.getDosage())
                                .frequency(item.getFrequency())
                                .durationDays(item.getDurationDays())
                                .instructions(item.getInstructions())
                                .build();
                    })
                    .collect(Collectors.toList());
            prescriptions = prescriptionRepository.saveAll(prescriptions);
        }

        // Transition appointment to COMPLETED
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointment.setConsultationEnd(LocalDateTime.now());
        appointmentRepository.save(appointment);

        // Conditionally reset doctor status to ON_DUTY if no remaining IN_PROGRESS appointments
        Long remainingInProgress = appointmentRepository.countOtherInProgressByDoctor(
                doctor.getId(), LocalDate.now(), tenantId, appointment.getId());
        if (remainingInProgress == 0) {
            updateDoctorStatus(doctor.getId(), DoctorStatus.ON_DUTY);
        }

        log.info("Consultation submitted for appointment {} by doctor {}", request.getAppointmentId(), doctor.getId());

        return ConsultationResponse.builder()
                .appointmentId(appointment.getId())
                .appointmentStatus(AppointmentStatus.COMPLETED.name())
                .diagnosis(mapToDiagnosisResponse(diagnosis))
                .prescriptions(prescriptions.stream()
                        .map(this::mapToPrescriptionResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    /**
     * Retrieves consultation data for print preview: diagnosis + prescriptions + patient/doctor info.
     * Only accessible for IN_PROGRESS or COMPLETED appointments.
     */
    public PrintPreviewResponse getConsultationForPrint(Long appointmentId) {
        Long tenantId = TenantContext.getTenantId();

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));

        // Tenant isolation
        if (!appointment.getTenantId().equals(tenantId)) {
            throw new AccessDeniedException("Access denied");
        }

        // Status validation — only IN_PROGRESS or COMPLETED
        if (appointment.getStatus() != AppointmentStatus.IN_PROGRESS &&
                appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new InvalidStateTransitionException(
                    "Print preview is only available for IN_PROGRESS or COMPLETED appointments.");
        }

        Diagnosis diagnosis = diagnosisRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No diagnosis found for appointment " + appointmentId));

        Patient patient = appointment.getPatient();
        Doctor doctor = appointment.getDoctor();

        List<PrescriptionResponse> prescriptionResponses = diagnosis.getPrescriptions().stream()
                .map(this::mapToPrescriptionResponse)
                .collect(Collectors.toList());

        return PrintPreviewResponse.builder()
                .hospitalName(getTenantHospitalName())
                .doctorName(doctor.getUser().getFullName())
                .doctorSpecialization(doctor.getDepartment() != null ? doctor.getDepartment().getName() : null)
                .patientName(patient.getFirstName() + " " + patient.getLastName())
                .patientCode(patient.getPatientCode())
                .consultationDate(appointment.getAppointmentDate())
                .temperature(diagnosis.getTemperature())
                .bloodPressure(diagnosis.getBloodPressure())
                .weight(diagnosis.getWeight())
                .symptoms(diagnosis.getSymptoms())
                .investigations(diagnosis.getInvestigations())
                .diagnosis(diagnosis.getDiagnosis())
                .clinicalNotes(diagnosis.getClinicalNotes())
                .severity(diagnosis.getSeverity())
                .followUpDate(diagnosis.getFollowUpDate())
                .prescriptions(prescriptionResponses)
                .build();
    }

    /**
     * Returns today's BOOKED/CHECKED_IN/IN_PROGRESS appointments for the given doctor,
     * ordered by queue position.
     */
    public List<QueueEntryResponse> getDoctorQueue(Long doctorId) {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();

        List<Appointment> queue = appointmentRepository
                .findTodayQueueByDoctorAndTenant(doctorId, today, tenantId);

        // Filter to only active statuses (BOOKED, CHECKED_IN, IN_PROGRESS)
        List<Appointment> activeQueue = queue.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.BOOKED ||
                             a.getStatus() == AppointmentStatus.CHECKED_IN ||
                             a.getStatus() == AppointmentStatus.IN_PROGRESS)
                .collect(Collectors.toList());

        return activeQueue.stream()
                .map(this::mapToQueueEntryResponse)
                .collect(Collectors.toList());
    }

    /**
     * Returns all distinct patients who have ever been assigned to this doctor (tenant-scoped).
     */
    public List<PatientResponse> getDoctorPatients(Long doctorId) {
        Long tenantId = TenantContext.getTenantId();
        List<Patient> patients = appointmentRepository.findDistinctPatientsByDoctor(doctorId, tenantId);
        return patients.stream()
                .map(this::mapToPatientResponse)
                .collect(Collectors.toList());
    }

    // ─── Helper Methods ────────────────────────────────────────────

    /**
     * Resolves the authenticated doctor from the SecurityContext.
     */
    public Doctor getAuthenticatedDoctor() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found for current user"));
    }

    /**
     * Updates doctor status via DoctorAvailabilityService.
     */
    private void updateDoctorStatus(Long doctorId, DoctorStatus status) {
        DoctorStatusUpdateRequest statusRequest = new DoctorStatusUpdateRequest();
        statusRequest.setStatus(status);
        doctorAvailabilityService.updateStatus(doctorId, statusRequest);
    }

    /**
     * Returns the hospital/tenant name. Uses a simple fallback since tenant name
     * is not directly accessible from TenantContext.
     */
    private String getTenantHospitalName() {
        // The tenant key from context serves as the hospital identifier
        String tenantKey = TenantContext.getTenantKey();
        return tenantKey != null ? tenantKey : "Hospital";
    }

    private AppointmentResponse mapToAppointmentResponse(Appointment appointment) {
        return AppointmentResponse.builder()
                .id(appointment.getId())
                .patientId(appointment.getPatient().getId())
                .patientName(appointment.getPatient().getFirstName() + " " + appointment.getPatient().getLastName())
                .patientCode(appointment.getPatient().getPatientCode())
                .doctorId(appointment.getDoctor().getId())
                .doctorName(appointment.getDoctor().getUser().getFullName())
                .department(appointment.getDoctor().getDepartment() != null ?
                        appointment.getDoctor().getDepartment().getName() : null)
                .appointmentDate(appointment.getAppointmentDate())
                .appointmentTime(appointment.getAppointmentTime())
                .type(appointment.getType())
                .tokenNumber(appointment.getTokenNumber())
                .status(appointment.getStatus())
                .notes(appointment.getNotes())
                .cancellationReason(appointment.getCancellationReason())
                .checkedInAt(appointment.getCheckedInAt())
                .consultationStart(appointment.getConsultationStart())
                .consultationEnd(appointment.getConsultationEnd())
                .noShowMarkedAt(appointment.getNoShowMarkedAt())
                .createdAt(appointment.getCreatedAt())
                .build();
    }

    private DiagnosisResponse mapToDiagnosisResponse(Diagnosis diagnosis) {
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
                        .map(this::mapToPrescriptionResponse)
                        .collect(Collectors.toList()))
                .createdAt(diagnosis.getCreatedAt())
                .build();
    }

    private PrescriptionResponse mapToPrescriptionResponse(Prescription prescription) {
        return PrescriptionResponse.builder()
                .id(prescription.getId())
                .medicineId(prescription.getMedicine().getId())
                .medicineName(prescription.getMedicine().getName())
                .medicineStrength(prescription.getMedicine().getStrength())
                .medicineForm(prescription.getMedicine().getForm())
                .dosage(prescription.getDosage())
                .frequency(prescription.getFrequency())
                .durationDays(prescription.getDurationDays())
                .instructions(prescription.getInstructions())
                .build();
    }

    private QueueEntryResponse mapToQueueEntryResponse(Appointment a) {
        return QueueEntryResponse.builder()
                .appointmentId(a.getId())
                .patientId(a.getPatient().getId())
                .patientName(a.getPatient().getFirstName() + " " + a.getPatient().getLastName())
                .patientCode(a.getPatient().getPatientCode())
                .doctorId(a.getDoctor().getId())
                .doctorName(a.getDoctor().getUser().getFullName())
                .tokenNumber(a.getTokenNumber())
                .appointmentTime(a.getAppointmentTime())
                .status(a.getStatus())
                .type(a.getType())
                .checkedInAt(a.getCheckedInAt())
                .build();
    }

    private PatientResponse mapToPatientResponse(Patient p) {
        return PatientResponse.builder()
                .id(p.getId())
                .patientCode(p.getPatientCode())
                .firstName(p.getFirstName())
                .lastName(p.getLastName())
                .dateOfBirth(p.getDateOfBirth())
                .gender(p.getGender())
                .phone(p.getPhone())
                .email(p.getEmail())
                .address(p.getAddress())
                .bloodGroup(p.getBloodGroup())
                .allergies(p.getAllergies())
                .medicalHistory(p.getMedicalHistory())
                .registeredAt(p.getRegisteredAt())
                .build();
    }
}
