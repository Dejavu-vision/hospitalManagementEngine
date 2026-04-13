package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.LabPrescriptionItemRequest;
import com.curamatrix.hsm.dto.request.LabPrescriptionRequest;
import com.curamatrix.hsm.dto.request.ReceptionistLabRegistrationRequest;
import com.curamatrix.hsm.dto.response.*;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.BillingItemType;
import com.curamatrix.hsm.enums.PaymentStatus;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LabPrescriptionService {

    private final LabPrescriptionRepository labPrescriptionRepository;
    private final LabServiceRepository labServiceRepository;
    private final LabTestRepository labTestRepository;
    private final LabServiceService labServiceService;
    private final BillingRepository billingRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;

    // ==================== 7.1: Doctor Digital Prescription Workflow ====================

    @Transactional
    public LabPrescriptionResponse createDoctorPrescription(LabPrescriptionRequest request) {
        Long tenantId = TenantContext.getTenantId();
        User currentUser = getAuthenticatedUser();

        // Look up patient
        Patient patient = patientRepository.findByIdAndTenantId(request.getPatientId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", request.getPatientId()));

        // Look up appointment
        Appointment appointment = appointmentRepository.findById(request.getAppointmentId())
                .filter(a -> a.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", request.getAppointmentId()));

        // Look up doctor from authenticated user
        Doctor doctor = doctorRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "userId", currentUser.getId()));

        // Validate all lab services are active and resolve prices
        List<LabService> labServices = validateAndFetchLabServices(request.getItems(), tenantId);

        // Create LabPrescription
        LabPrescription prescription = LabPrescription.builder()
                .patient(patient)
                .doctor(doctor)
                .appointment(appointment)
                .createdBy(currentUser)
                .build();
        prescription.setTenantId(tenantId);

        // Create LabTests
        List<LabTest> labTests = createLabTests(request.getItems(), labServices, prescription, tenantId);
        prescription.setLabTests(labTests);

        LabPrescription savedPrescription = labPrescriptionRepository.save(prescription);

        // Create BillingItems and associate with Billing
        Billing billing = findOrCreateBillingForAppointment(appointment, patient, tenantId);
        createBillingItems(labTests, labServices, billing);
        billingRepository.save(billing);

        log.info("Doctor prescription created: id={}, patientId={}, appointmentId={}, tests={}",
                savedPrescription.getId(), patient.getId(), appointment.getId(), labTests.size());

        return mapToResponse(savedPrescription);
    }

    // ==================== 7.2: Receptionist Lab Registration Workflow ====================

    @Transactional
    public LabPrescriptionResponse createReceptionistRegistration(ReceptionistLabRegistrationRequest request) {
        Long tenantId = TenantContext.getTenantId();
        User currentUser = getAuthenticatedUser();

        // Look up patient
        Patient patient = patientRepository.findByIdAndTenantId(request.getPatientId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", request.getPatientId()));

        // Validate all lab services are active and resolve prices
        List<LabService> labServices = validateAndFetchLabServices(request.getItems(), tenantId);

        // Create LabPrescription with doctor = null (handwritten origin)
        LabPrescription prescription = LabPrescription.builder()
                .patient(patient)
                .doctor(null)
                .appointment(null)
                .createdBy(currentUser)
                .build();
        prescription.setTenantId(tenantId);

        // Create LabTests
        List<LabTest> labTests = createLabTests(request.getItems(), labServices, prescription, tenantId);
        prescription.setLabTests(labTests);

        LabPrescription savedPrescription = labPrescriptionRepository.save(prescription);

        // Create BillingItems and associate with Billing (no appointment)
        Billing billing = findOrCreateBillingForPatient(patient, tenantId);
        createBillingItems(labTests, labServices, billing);
        billingRepository.save(billing);

        log.info("Receptionist lab registration created: id={}, patientId={}, tests={}",
                savedPrescription.getId(), patient.getId(), labTests.size());

        return mapToResponse(savedPrescription);
    }

    // ==================== 7.3: Prescription Retrieval Methods ====================

    @Transactional(readOnly = true)
    public List<LabPrescriptionResponse> getPrescriptionsByPatient(Long patientId) {
        Long tenantId = TenantContext.getTenantId();
        List<LabPrescription> prescriptions = labPrescriptionRepository
                .findByPatientIdAndTenantIdOrderByCreatedAtDesc(patientId, tenantId);
        return prescriptions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LabPrescriptionResponse getPrescriptionById(Long id) {
        Long tenantId = TenantContext.getTenantId();
        LabPrescription prescription = labPrescriptionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LabPrescription", "id", id));
        return mapToResponse(prescription);
    }

    // ==================== Helper Methods ====================

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private List<LabService> validateAndFetchLabServices(List<LabPrescriptionItemRequest> items, Long tenantId) {
        List<LabService> labServices = new ArrayList<>();
        for (LabPrescriptionItemRequest item : items) {
            LabService labService = labServiceRepository.findByIdAndTenantId(item.getLabServiceId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("LabService", "id", item.getLabServiceId()));
            if (!labService.isActive()) {
                throw new IllegalArgumentException(
                        "Lab service '" + labService.getServiceName() + "' (ID: " + labService.getId() + ") is inactive");
            }
            labServices.add(labService);
        }
        return labServices;
    }

    private List<LabTest> createLabTests(List<LabPrescriptionItemRequest> items,
                                          List<LabService> labServices,
                                          LabPrescription prescription,
                                          Long tenantId) {
        List<LabTest> labTests = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            LabPrescriptionItemRequest item = items.get(i);
            LabService labService = labServices.get(i);
            BigDecimal resolvedPrice = labServiceService.resolvePrice(labService.getId(), tenantId);

            LabTest labTest = LabTest.builder()
                    .labPrescription(prescription)
                    .labService(labService)
                    .testDate(LocalDate.now())
                    .notes(item.getNotes())
                    .billedPrice(resolvedPrice)
                    .build();
            labTest.setTenantId(tenantId);
            labTests.add(labTest);
        }
        return labTests;
    }

    private Billing findOrCreateBillingForAppointment(Appointment appointment, Patient patient, Long tenantId) {
        return billingRepository.findByAppointmentIdAndTenantId(appointment.getId(), tenantId)
                .orElseGet(() -> {
                    Billing newBilling = Billing.builder()
                            .appointment(appointment)
                            .patient(patient)
                            .invoiceNumber("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                            .totalAmount(BigDecimal.ZERO)
                            .netAmount(BigDecimal.ZERO)
                            .paymentStatus(PaymentStatus.PENDING)
                            .build();
                    newBilling.setTenantId(tenantId);
                    return newBilling;
                });
    }

    private Billing findOrCreateBillingForPatient(Patient patient, Long tenantId) {
        // Find the most recent pending billing for this patient (without appointment)
        List<Billing> patientBillings = billingRepository.findAllByPatientIdAndTenantId(patient.getId(), tenantId);
        return patientBillings.stream()
                .filter(b -> b.getAppointment() == null && b.getPaymentStatus() == PaymentStatus.PENDING)
                .findFirst()
                .orElseGet(() -> {
                    Billing newBilling = Billing.builder()
                            .patient(patient)
                            .invoiceNumber("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                            .totalAmount(BigDecimal.ZERO)
                            .netAmount(BigDecimal.ZERO)
                            .paymentStatus(PaymentStatus.PENDING)
                            .build();
                    newBilling.setTenantId(tenantId);
                    return newBilling;
                });
    }

    private void createBillingItems(List<LabTest> labTests, List<LabService> labServices, Billing billing) {
        for (int i = 0; i < labTests.size(); i++) {
            LabTest labTest = labTests.get(i);
            LabService labService = labServices.get(i);

            BillingItem billingItem = BillingItem.builder()
                    .billing(billing)
                    .description(labService.getServiceName())
                    .amount(labTest.getBilledPrice())
                    .quantity(1)
                    .itemType(BillingItemType.LAB)
                    .build();
            billing.getItems().add(billingItem);
        }

        // Update billing totals
        BigDecimal totalLabAmount = labTests.stream()
                .map(LabTest::getBilledPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        billing.setTotalAmount(billing.getTotalAmount().add(totalLabAmount));
        billing.setNetAmount(billing.getTotalAmount().subtract(billing.getDiscount()).add(billing.getTax()));
    }

    // ==================== DTO Mapping ====================

    private LabPrescriptionResponse mapToResponse(LabPrescription prescription) {
        return LabPrescriptionResponse.builder()
                .id(prescription.getId())
                .patientId(prescription.getPatient().getId())
                .patientName(prescription.getPatient().getFirstName() + " " + prescription.getPatient().getLastName())
                .doctorId(prescription.getDoctor() != null ? prescription.getDoctor().getId() : null)
                .doctorName(prescription.getDoctor() != null ? prescription.getDoctor().getUser().getFullName() : null)
                .createdByName(prescription.getCreatedBy().getFullName())
                .createdAt(prescription.getCreatedAt())
                .labTests(prescription.getLabTests().stream()
                        .map(this::mapLabTestToResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    private LabTestResponse mapLabTestToResponse(LabTest labTest) {
        return LabTestResponse.builder()
                .id(labTest.getId())
                .labServiceId(labTest.getLabService().getId())
                .labServiceName(labTest.getLabService().getServiceName())
                .category(labTest.getLabService().getCategory())
                .status(labTest.getStatus())
                .testDate(labTest.getTestDate())
                .notes(labTest.getNotes())
                .billedPrice(labTest.getBilledPrice())
                .startedAt(labTest.getStartedAt())
                .completedAt(labTest.getCompletedAt())
                .cancellationReason(labTest.getCancellationReason())
                .results(labTest.getResults() != null
                        ? labTest.getResults().stream().map(this::mapResultToResponse).collect(Collectors.toList())
                        : List.of())
                .documents(labTest.getDocuments() != null
                        ? labTest.getDocuments().stream().map(this::mapDocumentToResponse).collect(Collectors.toList())
                        : List.of())
                .build();
    }

    private LabResultResponse mapResultToResponse(LabResult result) {
        return LabResultResponse.builder()
                .id(result.getId())
                .parameterName(result.getParameterName())
                .resultValue(result.getResultValue())
                .unit(result.getUnit())
                .normalRangeLow(result.getNormalRangeLow())
                .normalRangeHigh(result.getNormalRangeHigh())
                .observations(result.getObservations())
                .enteredByName(result.getEnteredBy().getFullName())
                .enteredAt(result.getEnteredAt())
                .build();
    }

    private LabDocumentResponse mapDocumentToResponse(LabDocument document) {
        return LabDocumentResponse.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .uploadedByName(document.getUploadedBy().getFullName())
                .uploadedAt(document.getUploadedAt())
                .downloadUrl("/api/lab-tests/documents/" + document.getId() + "/download")
                .build();
    }
}
