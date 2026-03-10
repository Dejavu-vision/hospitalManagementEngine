package com.curamatrix.hsm.service;

import com.curamatrix.hsm.dto.request.AppointmentRequest;
import com.curamatrix.hsm.dto.request.WalkInRequest;
import com.curamatrix.hsm.dto.response.AppointmentResponse;
import com.curamatrix.hsm.dto.response.SlotResponse;
import com.curamatrix.hsm.entity.Appointment;
import com.curamatrix.hsm.entity.Doctor;
import com.curamatrix.hsm.entity.Patient;
import com.curamatrix.hsm.entity.User;
import com.curamatrix.hsm.enums.AppointmentStatus;
import com.curamatrix.hsm.enums.AppointmentType;
import com.curamatrix.hsm.repository.AppointmentRepository;
import com.curamatrix.hsm.repository.DoctorRepository;
import com.curamatrix.hsm.repository.PatientRepository;
import com.curamatrix.hsm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;

    @Transactional
    public AppointmentResponse bookAppointment(AppointmentRequest request) {
        // Validate slot availability
        appointmentRepository.findByDoctorAndDateAndTime(
                request.getDoctorId(), request.getAppointmentDate(), request.getAppointmentTime())
                .ifPresent(a -> {
                    throw new RuntimeException("Time slot already booked");
                });

        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        Doctor doctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        User bookedBy = getCurrentUser();

        Appointment appointment = Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .bookedBy(bookedBy)
                .appointmentDate(request.getAppointmentDate())
                .appointmentTime(request.getAppointmentTime())
                .type(AppointmentType.SCHEDULED)
                .status(AppointmentStatus.BOOKED)
                .notes(request.getNotes())
                .build();

        appointment = appointmentRepository.save(appointment);
        log.info("Appointment booked: {}", appointment.getId());

        return mapToResponse(appointment);
    }

    @Transactional
    public AppointmentResponse createWalkIn(WalkInRequest request) {
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        Doctor doctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        User bookedBy = getCurrentUser();

        LocalDate today = LocalDate.now();
        Integer maxToken = appointmentRepository.findMaxTokenNumber(request.getDoctorId(), today);
        int nextToken = (maxToken == null) ? 1 : maxToken + 1;

        Appointment appointment = Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .bookedBy(bookedBy)
                .appointmentDate(today)
                .type(AppointmentType.WALK_IN)
                .tokenNumber(nextToken)
                .status(AppointmentStatus.BOOKED)
                .notes(request.getNotes())
                .build();

        appointment = appointmentRepository.save(appointment);
        log.info("Walk-in appointment created with token: {}", nextToken);

        return mapToResponse(appointment);
    }

    public Page<AppointmentResponse> getAppointments(LocalDate date, Long doctorId, Long patientId,
                                                     AppointmentStatus status, AppointmentType type, Pageable pageable) {
        return appointmentRepository.findByFilters(date, doctorId, patientId, status, type, pageable)
                .map(this::mapToResponse);
    }

    public SlotResponse getAvailableSlots(Long doctorId, LocalDate date) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        List<String> allSlots = generateTimeSlots();
        List<Appointment> bookedAppointments = appointmentRepository.findByDoctorIdAndAppointmentDate(
                doctorId, date, Pageable.unpaged()).getContent();

        List<String> bookedSlots = bookedAppointments.stream()
                .filter(a -> a.getAppointmentTime() != null)
                .map(a -> a.getAppointmentTime().toString())
                .collect(Collectors.toList());

        List<String> availableSlots = allSlots.stream()
                .filter(slot -> !bookedSlots.contains(slot))
                .collect(Collectors.toList());

        return SlotResponse.builder()
                .doctorId(doctorId)
                .doctorName(doctor.getUser().getFullName())
                .date(date)
                .slotDurationMinutes(30)
                .availableSlots(availableSlots)
                .bookedSlots(bookedSlots)
                .build();
    }

    public List<AppointmentResponse> getTodayQueue(Long doctorId) {
        LocalDate today = LocalDate.now();
        return appointmentRepository.findTodayQueueByDoctor(doctorId, today).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AppointmentResponse updateStatus(Long id, AppointmentStatus status) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
        
        appointment.setStatus(status);
        appointment = appointmentRepository.save(appointment);
        
        return mapToResponse(appointment);
    }

    private List<String> generateTimeSlots() {
        List<String> slots = new ArrayList<>();
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(17, 0);
        
        while (start.isBefore(end)) {
            slots.add(start.toString());
            start = start.plusMinutes(30);
        }
        
        return slots;
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private AppointmentResponse mapToResponse(Appointment appointment) {
        return AppointmentResponse.builder()
                .id(appointment.getId())
                .patientId(appointment.getPatient().getId())
                .patientName(appointment.getPatient().getFirstName() + " " + appointment.getPatient().getLastName())
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
                .createdAt(appointment.getCreatedAt())
                .build();
    }
}
