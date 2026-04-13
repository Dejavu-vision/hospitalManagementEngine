package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.LabResultRequest;
import com.curamatrix.hsm.dto.request.LabTestStatusUpdateRequest;
import com.curamatrix.hsm.dto.response.*;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.BillingItemType;
import com.curamatrix.hsm.enums.ServiceCategory;
import com.curamatrix.hsm.enums.TestStatus;
import com.curamatrix.hsm.exception.InvalidStateTransitionException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LabTestService {

    private final LabTestRepository labTestRepository;
    private final LabResultRepository labResultRepository;
    private final LabDocumentRepository labDocumentRepository;
    private final LabTestStatusLogRepository labTestStatusLogRepository;
    private final BillingRepository billingRepository;
    private final UserRepository userRepository;

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_FILE_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png"
    );

    // ==================== 8.1: Test Status Management ====================

    @Transactional
    public LabTestResponse updateTestStatus(Long testId, LabTestStatusUpdateRequest request) {
        Long tenantId = TenantContext.getTenantId();
        User currentUser = getAuthenticatedUser();

        LabTest labTest = labTestRepository.findByIdAndTenantId(testId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LabTest", "id", testId));

        TestStatus currentStatus = labTest.getStatus();
        TestStatus newStatus = request.getNewStatus();

        // Validate transition
        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new InvalidStateTransitionException("LabTest", currentStatus.name(), newStatus.name());
        }

        // Require at least one result before completing
        if (newStatus == TestStatus.COMPLETED && !labResultRepository.existsByLabTestId(testId)) {
            throw new InvalidStateTransitionException("Cannot complete test: no results have been entered");
        }

        // Set timestamps based on transition
        if (newStatus == TestStatus.IN_PROGRESS) {
            labTest.setStartedAt(LocalDateTime.now());
        } else if (newStatus == TestStatus.COMPLETED) {
            labTest.setCompletedAt(LocalDateTime.now());
        }

        // Record cancellation reason
        if (newStatus == TestStatus.CANCELLED) {
            labTest.setCancellationReason(request.getCancellationReason());
            handleBillingCancellation(labTest);
        }

        labTest.setStatus(newStatus);
        labTest = labTestRepository.save(labTest);

        // Create status log entry
        createStatusLog(labTest, currentStatus, newStatus, currentUser, request.getCancellationReason());

        log.info("Lab test status updated: id={}, {} -> {}, tenantId={}", testId, currentStatus, newStatus, tenantId);
        return mapLabTestToResponse(labTest);
    }

    private void handleBillingCancellation(LabTest labTest) {
        String labServiceName = labTest.getLabService().getServiceName();
        LabPrescription prescription = labTest.getLabPrescription();
        Patient patient = prescription.getPatient();
        Long tenantId = labTest.getTenantId();

        // Find the billing associated with this test's prescription
        List<Billing> billings;
        if (prescription.getAppointment() != null) {
            billings = billingRepository.findByAppointmentIdAndTenantId(
                    prescription.getAppointment().getId(), tenantId)
                    .map(List::of)
                    .orElse(List.of());
        } else {
            billings = billingRepository.findAllByPatientIdAndTenantId(patient.getId(), tenantId);
        }

        for (Billing billing : billings) {
            BillingItem itemToRemove = billing.getItems().stream()
                    .filter(item -> item.getItemType() == BillingItemType.LAB
                            && item.getDescription() != null
                            && item.getDescription().contains(labServiceName))
                    .findFirst()
                    .orElse(null);

            if (itemToRemove != null) {
                BigDecimal removedAmount = itemToRemove.getAmount();
                billing.getItems().remove(itemToRemove);
                billing.setTotalAmount(billing.getTotalAmount().subtract(removedAmount));
                billing.setNetAmount(billing.getTotalAmount().subtract(billing.getDiscount()).add(billing.getTax()));
                billingRepository.save(billing);
                log.info("Removed billing item for cancelled lab test: testId={}, amount={}", labTest.getId(), removedAmount);
                break;
            }
        }
    }

    private void createStatusLog(LabTest labTest, TestStatus previousStatus, TestStatus newStatus,
                                  User changedBy, String reason) {
        LabTestStatusLog statusLog = LabTestStatusLog.builder()
                .labTest(labTest)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .changedBy(changedBy)
                .reason(reason)
                .build();
        statusLog.setTenantId(labTest.getTenantId());
        labTestStatusLogRepository.save(statusLog);
    }

    // ==================== 8.5: Lab Result Entry and Update ====================

    @Transactional
    public LabResultResponse addResult(Long testId, LabResultRequest request) {
        Long tenantId = TenantContext.getTenantId();
        User currentUser = getAuthenticatedUser();

        LabTest labTest = labTestRepository.findByIdAndTenantId(testId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LabTest", "id", testId));

        LabResult result = LabResult.builder()
                .labTest(labTest)
                .parameterName(request.getParameterName())
                .resultValue(request.getResultValue())
                .unit(request.getUnit())
                .normalRangeLow(request.getNormalRangeLow())
                .normalRangeHigh(request.getNormalRangeHigh())
                .observations(request.getObservations())
                .enteredBy(currentUser)
                .build();
        result.setTenantId(tenantId);

        result = labResultRepository.save(result);
        log.info("Lab result added: id={}, testId={}, tenantId={}", result.getId(), testId, tenantId);
        return mapResultToResponse(result);
    }

    @Transactional
    public LabResultResponse updateResult(Long testId, Long resultId, LabResultRequest request) {
        Long tenantId = TenantContext.getTenantId();

        LabTest labTest = labTestRepository.findByIdAndTenantId(testId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LabTest", "id", testId));

        if (labTest.getStatus() == TestStatus.COMPLETED) {
            throw new InvalidStateTransitionException("Cannot modify results: test is already completed");
        }

        LabResult result = labResultRepository.findByIdAndTenantId(resultId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LabResult", "id", resultId));

        result.setParameterName(request.getParameterName());
        result.setResultValue(request.getResultValue());
        result.setUnit(request.getUnit());
        result.setNormalRangeLow(request.getNormalRangeLow());
        result.setNormalRangeHigh(request.getNormalRangeHigh());
        result.setObservations(request.getObservations());

        result = labResultRepository.save(result);
        log.info("Lab result updated: id={}, testId={}, tenantId={}", resultId, testId, tenantId);
        return mapResultToResponse(result);
    }

    @Transactional(readOnly = true)
    public List<LabResultResponse> getResultsByTestId(Long testId) {
        Long tenantId = TenantContext.getTenantId();

        // Verify the test exists for this tenant
        labTestRepository.findByIdAndTenantId(testId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LabTest", "id", testId));

        return labResultRepository.findByLabTestIdAndTenantId(testId, tenantId)
                .stream()
                .map(this::mapResultToResponse)
                .collect(Collectors.toList());
    }

    // ==================== 8.8: Document Upload and Download ====================

    @Transactional
    public LabDocumentResponse uploadDocument(Long testId, MultipartFile file) {
        Long tenantId = TenantContext.getTenantId();
        User currentUser = getAuthenticatedUser();

        LabTest labTest = labTestRepository.findByIdAndTenantId(testId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LabTest", "id", testId));

        // Validate test status
        if (labTest.getStatus() == TestStatus.PENDING || labTest.getStatus() == TestStatus.CANCELLED) {
            throw new IllegalArgumentException(
                    "Cannot upload documents for a test with status " + labTest.getStatus().name());
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_FILE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported file type. Allowed types: PDF, JPEG, PNG");
        }

        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File size exceeds the maximum allowed size of 10MB");
        }

        // Store file in tenant-isolated directory
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
        String filePath = String.format("uploads/%d/lab-documents/%d/%s", tenantId, testId, originalFilename);
        Path targetPath = Paths.get(filePath);

        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, file.getBytes());
        } catch (IOException e) {
            log.error("Failed to store document: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to store document", e);
        }

        // Create LabDocument record
        LabDocument document = LabDocument.builder()
                .labTest(labTest)
                .fileName(originalFilename)
                .filePath(filePath)
                .fileType(contentType)
                .fileSize(file.getSize())
                .uploadedBy(currentUser)
                .build();
        document.setTenantId(tenantId);

        document = labDocumentRepository.save(document);
        log.info("Document uploaded: id={}, testId={}, fileName={}, tenantId={}",
                document.getId(), testId, originalFilename, tenantId);
        return mapDocumentToResponse(document);
    }

    @Transactional(readOnly = true)
    public Resource downloadDocument(Long docId) {
        Long tenantId = TenantContext.getTenantId();

        LabDocument document = labDocumentRepository.findByIdAndTenantId(docId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LabDocument", "id", docId));

        try {
            Path filePath = Paths.get(document.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("LabDocument", "file", document.getFilePath());
            }
            return resource;
        } catch (IOException e) {
            log.error("Failed to read document: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to read document", e);
        }
    }

    @Transactional(readOnly = true)
    public LabDocumentResponse getDocumentMetadata(Long docId) {
        Long tenantId = TenantContext.getTenantId();
        LabDocument document = labDocumentRepository.findByIdAndTenantId(docId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LabDocument", "id", docId));
        return mapDocumentToResponse(document);
    }

    @Transactional(readOnly = true)
    public List<LabDocumentResponse> getDocumentsByTestId(Long testId) {
        Long tenantId = TenantContext.getTenantId();

        // Verify the test exists for this tenant
        labTestRepository.findByIdAndTenantId(testId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LabTest", "id", testId));

        return labDocumentRepository.findByLabTestIdAndTenantId(testId, tenantId)
                .stream()
                .map(this::mapDocumentToResponse)
                .collect(Collectors.toList());
    }

    // ==================== 8.11: Dashboard Queries ====================

    @Transactional(readOnly = true)
    public List<LabTestDashboardResponse> getTodayTests(TestStatus status, ServiceCategory category) {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();

        List<LabTest> tests;
        if (status != null) {
            tests = labTestRepository.findByTestDateAndTenantIdAndStatus(today, tenantId, status);
        } else {
            tests = labTestRepository.findByTestDateAndTenantIdOrderByCreatedAtAsc(today, tenantId);
        }

        // Apply category filter in-memory if provided
        if (category != null) {
            tests = tests.stream()
                    .filter(t -> t.getLabService().getCategory() == category)
                    .collect(Collectors.toList());
        }

        return tests.stream()
                .map(this::mapToDashboardResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LabTestSummaryResponse getTodaySummary() {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();

        List<Object[]> statusCounts = labTestRepository.countByStatusForDate(today, tenantId);

        long totalPending = 0;
        long totalInProgress = 0;
        long totalCompleted = 0;
        long totalCancelled = 0;

        for (Object[] row : statusCounts) {
            TestStatus rowStatus = (TestStatus) row[0];
            long count = (Long) row[1];
            switch (rowStatus) {
                case PENDING -> totalPending = count;
                case IN_PROGRESS -> totalInProgress = count;
                case COMPLETED -> totalCompleted = count;
                case CANCELLED -> totalCancelled = count;
            }
        }

        return LabTestSummaryResponse.builder()
                .totalPending(totalPending)
                .totalInProgress(totalInProgress)
                .totalCompleted(totalCompleted)
                .totalCancelled(totalCancelled)
                .build();
    }

    // ==================== Helper Methods ====================

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private LabTestDashboardResponse mapToDashboardResponse(LabTest labTest) {
        Patient patient = labTest.getLabPrescription().getPatient();
        Doctor doctor = labTest.getLabPrescription().getDoctor();

        return LabTestDashboardResponse.builder()
                .id(labTest.getId())
                .patientName(patient.getFirstName() + " " + patient.getLastName())
                .patientCode(patient.getPatientCode())
                .labServiceName(labTest.getLabService().getServiceName())
                .category(labTest.getLabService().getCategory())
                .status(labTest.getStatus())
                .doctorName(doctor != null ? doctor.getUser().getFullName() : null)
                .notes(labTest.getNotes())
                .createdAt(labTest.getCreatedAt())
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
