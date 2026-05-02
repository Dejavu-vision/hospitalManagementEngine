package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.PreAuthCoverageItemDto;
import com.curamatrix.hsm.dto.request.PreAuthRequestDto;
import com.curamatrix.hsm.dto.response.PreAuthCoverageItemResponse;
import com.curamatrix.hsm.dto.response.PreAuthResponseDto;
import com.curamatrix.hsm.entity.InsurancePolicy;
import com.curamatrix.hsm.entity.Patient;
import com.curamatrix.hsm.entity.PreAuthCoverageItem;
import com.curamatrix.hsm.entity.PreAuthRequest;
import com.curamatrix.hsm.enums.PreAuthStatus;
import com.curamatrix.hsm.exception.InvalidStateTransitionException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.InsurancePolicyRepository;
import com.curamatrix.hsm.repository.PatientRepository;
import com.curamatrix.hsm.repository.PreAuthCoverageItemRepository;
import com.curamatrix.hsm.repository.PreAuthRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreAuthService {

    private final PreAuthRequestRepository preAuthRepository;
    private final PatientRepository patientRepository;
    private final InsurancePolicyRepository insurancePolicyRepository;
    private final PreAuthCoverageItemRepository coverageItemRepository;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public PreAuthResponseDto createPreAuth(PreAuthRequestDto dto) {
        Long tenantId = TenantContext.getTenantId();

        Patient patient = patientRepository.findByIdAndTenantId(dto.getPatientId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", dto.getPatientId()));

        InsurancePolicy policy = insurancePolicyRepository.findByIdAndTenantId(dto.getInsurancePolicyId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("InsurancePolicy", "id", dto.getInsurancePolicyId()));

        PreAuthRequest request = PreAuthRequest.builder()
                .patient(patient)
                .insurancePolicy(policy)
                .admissionId(dto.getAdmissionId())
                .appointmentId(dto.getAppointmentId())
                .claimType(dto.getClaimType() != null ? dto.getClaimType() : "CASHLESS")
                .diagnosisCode(dto.getDiagnosisCode())
                .procedureCode(dto.getProcedureCode())
                .estimatedAmount(dto.getEstimatedAmount())
                .approvedAmount(dto.getApprovedAmount())
                .status(dto.getStatus() != null ? dto.getStatus() : PreAuthStatus.DRAFT)
                .tpaReferenceNumber(dto.getTpaReferenceNumber())
                .remarks(dto.getRemarks())
                .isEnhancement(dto.getIsEnhancement() != null && dto.getIsEnhancement())
                .parentPreAuthId(dto.getParentPreAuthId())
                .requestedAt(LocalDateTime.now())
                .build();
        request.setTenantId(tenantId);
        request = preAuthRepository.save(request);

        // Save category-wise coverage items if provided
        saveCoverageItems(request, dto.getCoverageItems(), tenantId);

        log.info("Created PreAuthRequest {} (type={}, enhancement={}) for Patient {}",
                request.getId(), request.getClaimType(), request.getIsEnhancement(), patient.getId());

        return mapToResponse(request);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public PreAuthResponseDto updatePreAuth(Long id, PreAuthRequestDto dto) {
        Long tenantId = TenantContext.getTenantId();

        PreAuthRequest request = preAuthRepository.findById(id)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthRequest", "id", id));

        if (dto.getEstimatedAmount() != null) request.setEstimatedAmount(dto.getEstimatedAmount());
        if (dto.getApprovedAmount() != null) request.setApprovedAmount(dto.getApprovedAmount());
        if (dto.getRemarks() != null) request.setRemarks(dto.getRemarks());
        if (dto.getTpaReferenceNumber() != null) request.setTpaReferenceNumber(dto.getTpaReferenceNumber());
        if (dto.getDiagnosisCode() != null) request.setDiagnosisCode(dto.getDiagnosisCode());
        if (dto.getProcedureCode() != null) request.setProcedureCode(dto.getProcedureCode());
        if (dto.getClaimType() != null) request.setClaimType(dto.getClaimType());

        if (dto.getStatus() != null && request.getStatus() != dto.getStatus()) {
            request.setStatus(dto.getStatus());
            if (dto.getStatus() == PreAuthStatus.APPROVED || dto.getStatus() == PreAuthStatus.REJECTED) {
                request.setResolvedAt(LocalDateTime.now());
            }
        }

        request = preAuthRepository.save(request);

        // Replace coverage items if provided
        if (dto.getCoverageItems() != null) {
            coverageItemRepository.deleteByPreAuthId(request.getId());
            saveCoverageItems(request, dto.getCoverageItems(), tenantId);
        }

        return mapToResponse(request);
    }

    // ── Enhancement pre-auth ──────────────────────────────────────────────────

    /**
     * Creates an enhancement pre-auth when the initial approved amount is nearly exhausted.
     * Apollo/Fortis hospitals do this when a patient's stay extends beyond the initial estimate.
     */
    @Transactional
    public PreAuthResponseDto createEnhancement(Long parentPreAuthId, PreAuthRequestDto dto) {
        Long tenantId = TenantContext.getTenantId();

        PreAuthRequest parent = preAuthRepository.findById(parentPreAuthId)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthRequest", "id", parentPreAuthId));

        if (parent.getStatus() != PreAuthStatus.APPROVED) {
            throw new InvalidStateTransitionException("PreAuthRequest",
                    parent.getStatus().name(), "ENHANCEMENT_REQUIRES_APPROVED_PARENT");
        }

        // Inherit from parent
        dto.setPatientId(parent.getPatient().getId());
        dto.setInsurancePolicyId(parent.getInsurancePolicy().getId());
        dto.setAdmissionId(parent.getAdmissionId());
        dto.setIsEnhancement(true);
        dto.setParentPreAuthId(parentPreAuthId);
        if (dto.getClaimType() == null) dto.setClaimType(parent.getClaimType());
        if (dto.getDiagnosisCode() == null) dto.setDiagnosisCode(parent.getDiagnosisCode());

        log.info("Creating enhancement pre-auth for parent {} (admission {})", parentPreAuthId, parent.getAdmissionId());
        return createPreAuth(dto);
    }

    // ── TPA query response ────────────────────────────────────────────────────

    /**
     * Hospital responds to a TPA query (e.g., TPA asks for more clinical documents).
     * Status moves back to SUBMITTED after the response is recorded.
     */
    @Transactional
    public PreAuthResponseDto respondToQuery(Long id, String queryResponse) {
        Long tenantId = TenantContext.getTenantId();

        PreAuthRequest request = preAuthRepository.findById(id)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthRequest", "id", id));

        if (request.getStatus() != PreAuthStatus.QUERY_RAISED) {
            throw new InvalidStateTransitionException("PreAuthRequest",
                    request.getStatus().name(), "QUERY_RAISED");
        }

        request.setQueryResponse(queryResponse);
        request.setStatus(PreAuthStatus.SUBMITTED); // Re-submitted after answering query
        request = preAuthRepository.save(request);

        log.info("Query response recorded for PreAuthRequest {}", id);
        return mapToResponse(request);
    }

    // ── Final claim at discharge ──────────────────────────────────────────────

    /**
     * Submit the final claim to TPA at the time of patient discharge.
     * The final claim amount is the actual total IPD bill amount.
     */
    @Transactional
    public PreAuthResponseDto submitFinalClaim(Long id, BigDecimal finalClaimAmount) {
        Long tenantId = TenantContext.getTenantId();

        PreAuthRequest request = preAuthRepository.findById(id)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthRequest", "id", id));

        request.setFinalClaimAmount(finalClaimAmount);
        request.setStatus(PreAuthStatus.SUBMITTED);
        request = preAuthRepository.save(request);

        log.info("Final claim ₹{} submitted for PreAuthRequest {}", finalClaimAmount, id);
        return mapToResponse(request);
    }

    /**
     * Record the TPA's final settlement (what they actually paid).
     * Called when the hospital receives the TPA payment.
     */
    @Transactional
    public PreAuthResponseDto settleClaim(Long id, BigDecimal settledAmount) {
        Long tenantId = TenantContext.getTenantId();

        PreAuthRequest request = preAuthRepository.findById(id)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthRequest", "id", id));

        request.setFinalSettledAmount(settledAmount);
        request.setStatus(PreAuthStatus.APPROVED);
        request.setResolvedAt(LocalDateTime.now());
        request = preAuthRepository.save(request);

        log.info("Claim settled: ₹{} received from TPA for PreAuthRequest {}", settledAmount, id);
        return mapToResponse(request);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Page<PreAuthResponseDto> getAllPreAuths(Pageable pageable) {
        Long tenantId = TenantContext.getTenantId();
        return preAuthRepository.findByTenantId(tenantId, pageable).map(this::mapToResponse);
    }

    public Page<PreAuthResponseDto> getPreAuthsByPatient(Long patientId, Pageable pageable) {
        Long tenantId = TenantContext.getTenantId();
        return preAuthRepository.findByPatientIdAndTenantId(patientId, tenantId, pageable).map(this::mapToResponse);
    }

    public List<PreAuthResponseDto> getPreAuthsByAdmission(Long admissionId) {
        Long tenantId = TenantContext.getTenantId();
        return preAuthRepository.findByAdmissionIdAndTenantId(admissionId, tenantId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void saveCoverageItems(PreAuthRequest request, List<PreAuthCoverageItemDto> items, Long tenantId) {
        if (items == null || items.isEmpty()) return;
        for (PreAuthCoverageItemDto dto : items) {
            PreAuthCoverageItem item = PreAuthCoverageItem.builder()
                    .preAuth(request)
                    .itemType(dto.getItemType())
                    .approvedAmount(dto.getApprovedAmount())
                    .dailyLimit(dto.getDailyLimit())
                    .remarks(dto.getRemarks())
                    .build();
            item.setTenantId(tenantId);
            coverageItemRepository.save(item);
        }
    }

    private PreAuthResponseDto mapToResponse(PreAuthRequest request) {
        List<PreAuthCoverageItemResponse> coverageItems = coverageItemRepository
                .findByPreAuthId(request.getId())
                .stream()
                .map(ci -> PreAuthCoverageItemResponse.builder()
                        .id(ci.getId())
                        .itemType(ci.getItemType())
                        .approvedAmount(ci.getApprovedAmount())
                        .dailyLimit(ci.getDailyLimit())
                        .remarks(ci.getRemarks())
                        .build())
                .collect(Collectors.toList());

        InsurancePolicy policy = request.getInsurancePolicy();
        String insurerName = policy.getPayer() != null ? policy.getPayer().getInsurerName() : "Unknown";
        String tpaName = policy.getPayer() != null ? policy.getPayer().getTpaName() : null;

        return PreAuthResponseDto.builder()
                .id(request.getId())
                .patientId(request.getPatient().getId())
                .patientName(request.getPatient().getFirstName() + " " + request.getPatient().getLastName())
                .patientCode(request.getPatient().getPatientCode())
                .insurancePolicyId(policy.getId())
                .policyNumber(policy.getPolicyNumber())
                .insurerName(insurerName)
                .tpaName(tpaName)
                .admissionId(request.getAdmissionId())
                .appointmentId(request.getAppointmentId())
                .claimType(request.getClaimType())
                .diagnosisCode(request.getDiagnosisCode())
                .procedureCode(request.getProcedureCode())
                .estimatedAmount(request.getEstimatedAmount())
                .approvedAmount(request.getApprovedAmount())
                .finalClaimAmount(request.getFinalClaimAmount())
                .finalSettledAmount(request.getFinalSettledAmount())
                .status(request.getStatus())
                .tpaReferenceNumber(request.getTpaReferenceNumber())
                .remarks(request.getRemarks())
                .queryResponse(request.getQueryResponse())
                .isEnhancement(request.getIsEnhancement())
                .parentPreAuthId(request.getParentPreAuthId())
                .coverageItems(coverageItems)
                .requestedAt(request.getRequestedAt())
                .resolvedAt(request.getResolvedAt())
                .build();
    }
}
