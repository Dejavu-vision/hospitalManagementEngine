package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.LabResultRequest;
import com.curamatrix.hsm.dto.request.LabTestStatusUpdateRequest;
import com.curamatrix.hsm.dto.response.*;
import com.curamatrix.hsm.enums.ServiceCategory;
import com.curamatrix.hsm.enums.TestStatus;
import com.curamatrix.hsm.service.LabTestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/lab-tests")
@RequiredArgsConstructor
public class LabTestController {

    private final LabTestService labTestService;

    // ==================== Dashboard Endpoints ====================

    @GetMapping("/today")
    @PreAuthorize("hasRole('LAB_STAFF')")
    public ResponseEntity<List<LabTestDashboardResponse>> getTodayTests(
            @RequestParam(required = false) TestStatus status,
            @RequestParam(required = false) ServiceCategory category) {
        List<LabTestDashboardResponse> response = labTestService.getTodayTests(status, category);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/today/summary")
    @PreAuthorize("hasRole('LAB_STAFF')")
    public ResponseEntity<LabTestSummaryResponse> getTodaySummary() {
        LabTestSummaryResponse response = labTestService.getTodaySummary();
        return ResponseEntity.ok(response);
    }

    // ==================== Status Management ====================

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('LAB_STAFF')")
    public ResponseEntity<LabTestResponse> updateTestStatus(@PathVariable Long id,
                                                             @Valid @RequestBody LabTestStatusUpdateRequest request) {
        log.info("Updating lab test status: id={}, newStatus={}", id, request.getNewStatus());
        LabTestResponse response = labTestService.updateTestStatus(id, request);
        return ResponseEntity.ok(response);
    }

    // ==================== Result Endpoints ====================

    @PostMapping("/{id}/results")
    @PreAuthorize("hasRole('LAB_STAFF')")
    public ResponseEntity<LabResultResponse> addResult(@PathVariable Long id,
                                                        @Valid @RequestBody LabResultRequest request) {
        log.info("Adding result for lab test: id={}", id);
        LabResultResponse response = labTestService.addResult(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{testId}/results/{resultId}")
    @PreAuthorize("hasRole('LAB_STAFF')")
    public ResponseEntity<LabResultResponse> updateResult(@PathVariable Long testId,
                                                           @PathVariable Long resultId,
                                                           @Valid @RequestBody LabResultRequest request) {
        log.info("Updating result: resultId={}, testId={}", resultId, testId);
        LabResultResponse response = labTestService.updateResult(testId, resultId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/results")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN', 'LAB_STAFF')")
    public ResponseEntity<List<LabResultResponse>> getResults(@PathVariable Long id) {
        List<LabResultResponse> response = labTestService.getResultsByTestId(id);
        return ResponseEntity.ok(response);
    }

    // ==================== Document Endpoints ====================

    @PostMapping("/{id}/documents")
    @PreAuthorize("hasRole('LAB_STAFF')")
    public ResponseEntity<LabDocumentResponse> uploadDocument(@PathVariable Long id,
                                                               @RequestParam("file") MultipartFile file) {
        log.info("Uploading document for lab test: id={}, fileName={}", id, file.getOriginalFilename());
        LabDocumentResponse response = labTestService.uploadDocument(id, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN', 'LAB_STAFF')")
    public ResponseEntity<List<LabDocumentResponse>> getDocuments(@PathVariable Long id) {
        List<LabDocumentResponse> response = labTestService.getDocumentsByTestId(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/documents/{docId}/download")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN', 'LAB_STAFF')")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long docId) {
        LabDocumentResponse metadata = labTestService.getDocumentMetadata(docId);
        Resource resource = labTestService.downloadDocument(docId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + metadata.getFileName() + "\"")
                .body(resource);
    }
}
