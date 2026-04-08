package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.response.BookingContextResponse;
import com.curamatrix.hsm.entity.Department;
import com.curamatrix.hsm.entity.Doctor;
import com.curamatrix.hsm.entity.DoctorAvailability;
import com.curamatrix.hsm.entity.Patient;
import com.curamatrix.hsm.entity.PatientRegistration;
import com.curamatrix.hsm.enums.DoctorStatus;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.curamatrix.hsm.dto.response.CasePaperResponse;
import com.curamatrix.hsm.entity.Billing;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReceptionDeskService {

    private final DoctorRepository doctorRepository;
    private final DoctorAvailabilityRepository doctorAvailabilityRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRegistrationRepository patientRegistrationRepository;
    private final PatientRepository patientRepository;
    private final DepartmentRepository departmentRepository;
    private final BillingService billingService;

    /**
     * Builds the composite booking context for a patient in a single request lifecycle.
     * Executes 5 queries: departments, doctors, availability, queue lengths, case paper.
     */
    public BookingContextResponse getBookingContext(Long patientId) {
        Long tenantId = TenantContext.getTenantId();
        log.info("Building booking context for patient {} in tenant {}", patientId, tenantId);

        // Validate patient exists
        patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId));

        // Query 1: All active departments (global, not tenant-scoped)
        List<Department> departments = departmentRepository.findByIsActiveTrue();

        // Query 2: All doctors for this tenant
        List<Doctor> doctors = doctorRepository.findByTenantId(tenantId);

        // Query 3: Today's availability records for this tenant
        LocalDate today = LocalDate.now();
        List<DoctorAvailability> availabilities = doctorAvailabilityRepository
                .findByAvailabilityDateAndTenantId(today, tenantId);

        // Query 4: Queue lengths per doctor for today
        List<Object[]> queueData = appointmentRepository
                .findQueueLengthsByTenant(today, tenantId);

        // Query 5: Patient's latest active registration (case paper)
        Optional<PatientRegistration> registration = patientRegistrationRepository
                .findLatestActiveRegistration(patientId, tenantId);

        // Build lookup maps for efficient assembly
        Map<Long, DoctorAvailability> availabilityByDoctorId = availabilities.stream()
                .collect(Collectors.toMap(
                        a -> a.getDoctor().getId(),
                        a -> a,
                        (a1, a2) -> a1 // in case of duplicates, take first
                ));

        Map<Long, Long> queueLengthByDoctorId = new HashMap<>();
        for (Object[] row : queueData) {
            Long doctorId = (Long) row[0];
            Long count = (Long) row[1];
            queueLengthByDoctorId.put(doctorId, count);
        }

        // Group doctors by department
        Map<Long, List<Doctor>> doctorsByDeptId = doctors.stream()
                .filter(d -> d.getDepartment() != null)
                .collect(Collectors.groupingBy(d -> d.getDepartment().getId()));

        // Assemble departments with doctors
        List<BookingContextResponse.DepartmentWithDoctors> deptResponses = departments.stream()
                .map(dept -> {
                    List<Doctor> deptDoctors = doctorsByDeptId.getOrDefault(dept.getId(), Collections.emptyList());

                    List<BookingContextResponse.DoctorInfo> doctorInfos = deptDoctors.stream()
                            .map(doctor -> {
                                DoctorAvailability avail = availabilityByDoctorId.get(doctor.getId());
                                boolean presentToday = avail != null && Boolean.TRUE.equals(avail.getIsPresent());
                                DoctorStatus status = avail != null ? avail.getStatus() : DoctorStatus.OFF_DUTY;
                                String statusNote = avail != null ? avail.getStatusNote() : null;
                                int queueLength = queueLengthByDoctorId
                                        .getOrDefault(doctor.getId(), 0L).intValue();

                                return BookingContextResponse.DoctorInfo.builder()
                                        .doctorId(doctor.getId())
                                        .doctorName(doctor.getUser().getFullName())
                                        .qualification(doctor.getQualification())
                                        .consultationFee(doctor.getConsultationFee())
                                        .presentToday(presentToday)
                                        .status(status)
                                        .statusNote(statusNote)
                                        .activeQueueLength(queueLength)
                                        .build();
                            })
                            .collect(Collectors.toList());

                    return BookingContextResponse.DepartmentWithDoctors.builder()
                            .departmentId(dept.getId())
                            .departmentName(dept.getName())
                            .doctors(doctorInfos)
                            .build();
                })
                .collect(Collectors.toList());

        // Build case paper status
        BookingContextResponse.CasePaperStatus casePaperStatus = buildCasePaperStatus(registration);

        return BookingContextResponse.builder()
                .patientId(patientId)
                .casePaper(casePaperStatus)
                .departments(deptResponses)
                .build();
    }

    /**
     * Creates a case paper (patient registration billing) for a patient.
     * Delegates to BillingService.createRegistrationBilling() and maps the result
     * into a CasePaperResponse.
     */
    @Transactional
    public CasePaperResponse createCasePaper(Long patientId, String paymentMethod) {
        Long tenantId = TenantContext.getTenantId();
        log.info("Creating case paper for patient {} in tenant {}", patientId, tenantId);

        // Validate patient exists
        Patient patient = patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId));

        // Delegate to BillingService to create the registration billing + case paper
        Billing billing = billingService.createRegistrationBilling(patient, tenantId, paymentMethod);

        // Fetch the newly created patient registration to get issuedAt and expiresAt
        PatientRegistration registration = patientRegistrationRepository
                .findLatestActiveRegistration(patientId, tenantId)
                .orElseThrow(() -> new RuntimeException("Case paper was not created successfully"));

        return CasePaperResponse.builder()
                .registrationId(registration.getId())
                .billingId(billing.getId())
                .invoiceNumber(billing.getInvoiceNumber())
                .issuedAt(registration.getIssuedAt())
                .expiresAt(registration.getExpiresAt())
                .amount(billing.getTotalAmount())
                .paymentStatus(billing.getPaymentStatus())
                .build();
    }


    private BookingContextResponse.CasePaperStatus buildCasePaperStatus(
            Optional<PatientRegistration> registration) {

        if (registration.isEmpty()) {
            return BookingContextResponse.CasePaperStatus.builder()
                    .valid(false)
                    .remainingDays(-1)
                    .expiringSoon(false)
                    .build();
        }

        PatientRegistration reg = registration.get();
        LocalDateTime now = LocalDateTime.now();

        if (reg.isExpired()) {
            return BookingContextResponse.CasePaperStatus.builder()
                    .valid(false)
                    .expiresAt(reg.getExpiresAt())
                    .registrationId(reg.getId())
                    .remainingDays(-1)
                    .expiringSoon(false)
                    .build();
        }

        long remainingDays = ChronoUnit.DAYS.between(now, reg.getExpiresAt());
        boolean expiringSoon = remainingDays <= 7;

        return BookingContextResponse.CasePaperStatus.builder()
                .valid(true)
                .expiresAt(reg.getExpiresAt())
                .registrationId(reg.getId())
                .remainingDays((int) remainingDays)
                .expiringSoon(expiringSoon)
                .build();
    }
}
