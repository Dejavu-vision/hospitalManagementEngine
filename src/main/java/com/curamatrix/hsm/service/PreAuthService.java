package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.PreAuthRequestDto;
import com.curamatrix.hsm.dto.response.PreAuthResponseDto;
import com.curamatrix.hsm.entity.InsurancePolicy;
import com.curamatrix.hsm.entity.Patient;
import com.curamatrix.hsm.entity.PreAuthRequest;
import com.curamatrix.hsm.enums.PreAuthStatus;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.InsurancePolicyRepository;
import com.curamatrix.hsm.repository.PatientRepository;
import com.curamatrix.hsm.repository.PreAuthRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreAuthService {

    private final PreAuthRequestRepository preAuthRepository;
    private final PatientRepository patientRepository;
    private final InsurancePolicyRepository insurancePolicyRepository;

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
                .estimatedAmount(dto.getEstimatedAmount())
                .approvedAmount(dto.getApprovedAmount())
                .status(dto.getStatus() != null ? dto.getStatus() : PreAuthStatus.DRAFT)
                .tpaReferenceNumber(dto.getTpaReferenceNumber())
                .remarks(dto.getRemarks())
                .requestedAt(LocalDateTime.now())
                .build();
        request.setTenantId(tenantId);

        request = preAuthRepository.save(request);
        log.info("Created PreAuthRequest {} for Patient {}", request.getId(), patient.getId());

        return mapToResponse(request);
    }

    @Transactional
    public PreAuthResponseDto updatePreAuth(Long id, PreAuthRequestDto dto) {
        Long tenantId = TenantContext.getTenantId();

        PreAuthRequest request = preAuthRepository.findById(id)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthRequest", "id", id));

        request.setEstimatedAmount(dto.getEstimatedAmount());
        request.setApprovedAmount(dto.getApprovedAmount());
        request.setRemarks(dto.getRemarks());
        request.setTpaReferenceNumber(dto.getTpaReferenceNumber());

        if (dto.getStatus() != null && request.getStatus() != dto.getStatus()) {
            request.setStatus(dto.getStatus());
            if (dto.getStatus() == PreAuthStatus.APPROVED || dto.getStatus() == PreAuthStatus.REJECTED) {
                request.setResolvedAt(LocalDateTime.now());
            }
        }

        request = preAuthRepository.save(request);
        return mapToResponse(request);
    }

    public Page<PreAuthResponseDto> getAllPreAuths(Pageable pageable) {
        Long tenantId = TenantContext.getTenantId();
        return preAuthRepository.findByTenantId(tenantId, pageable).map(this::mapToResponse);
    }

    public Page<PreAuthResponseDto> getPreAuthsByPatient(Long patientId, Pageable pageable) {
        Long tenantId = TenantContext.getTenantId();
        return preAuthRepository.findByPatientIdAndTenantId(patientId, tenantId, pageable).map(this::mapToResponse);
    }

    private PreAuthResponseDto mapToResponse(PreAuthRequest request) {
        return PreAuthResponseDto.builder()
                .id(request.getId())
                .patientId(request.getPatient().getId())
                .patientName(request.getPatient().getFirstName() + " " + request.getPatient().getLastName())
                .patientCode(request.getPatient().getPatientCode())
                .insurancePolicyId(request.getInsurancePolicy().getId())
                .policyNumber(request.getInsurancePolicy().getPolicyNumber())
                .insurerName(request.getInsurancePolicy().getPayer() != null ? request.getInsurancePolicy().getPayer().getInsurerName() : "Unknown")
                .admissionId(request.getAdmissionId())
                .appointmentId(request.getAppointmentId())
                .estimatedAmount(request.getEstimatedAmount())
                .approvedAmount(request.getApprovedAmount())
                .status(request.getStatus())
                .tpaReferenceNumber(request.getTpaReferenceNumber())
                .remarks(request.getRemarks())
                .requestedAt(request.getRequestedAt())
                .resolvedAt(request.getResolvedAt())
                .build();
    }
}
