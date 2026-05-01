package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.InsurancePolicyRequest;
import com.curamatrix.hsm.dto.response.InsurancePolicyResponse;
import com.curamatrix.hsm.entity.InsurancePolicy;
import com.curamatrix.hsm.entity.Patient;
import com.curamatrix.hsm.entity.PayerMaster;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.InsurancePolicyRepository;
import com.curamatrix.hsm.repository.PatientRepository;
import com.curamatrix.hsm.repository.PayerMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsurancePolicyService {

    private final InsurancePolicyRepository policyRepository;
    private final PatientRepository patientRepository;
    private final PayerMasterRepository payerMasterRepository;

    @Transactional(readOnly = true)
    public List<InsurancePolicyResponse> getPoliciesByPatient(Long patientId) {
        Long tenantId = TenantContext.getTenantId();
        return policyRepository.findByPatientIdAndTenantId(patientId, tenantId)
                .stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public InsurancePolicyResponse createPolicy(Long patientId, InsurancePolicyRequest dto) {
        Long tenantId = TenantContext.getTenantId();
        Patient patient = patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId));

        PayerMaster payer = null;
        if (dto.getPayerId() != null) {
            payer = payerMasterRepository.findById(dto.getPayerId()).orElse(null);
        }

        InsurancePolicy policy = InsurancePolicy.builder()
                .patient(patient)
                .payer(payer)
                .policyNumber(dto.getPolicyNumber())
                .memberId(dto.getMemberId())
                .sumInsured(dto.getSumInsured())
                .roomRentLimit(dto.getRoomRentLimit())
                .copayPct(dto.getCopayPct())
                .validFrom(dto.getValidFrom())
                .validTo(dto.getValidTo())
                .policyType(dto.getPolicyType())
                .build();
        policy.setTenantId(tenantId);

        // Also update patient legacy fields
        if (payer != null) patient.setInsuranceProvider(payer.getInsurerName());
        patient.setInsurancePolicyNumber(dto.getPolicyNumber());

        policy = policyRepository.save(policy);
        log.info("Created InsurancePolicy {} for patient {}", policy.getId(), patientId);
        return mapToResponse(policy);
    }

    @Transactional
    public InsurancePolicyResponse updatePolicy(Long id, InsurancePolicyRequest dto) {
        Long tenantId = TenantContext.getTenantId();
        InsurancePolicy policy = policyRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("InsurancePolicy", "id", id));

        PayerMaster payer = null;
        if (dto.getPayerId() != null) {
            payer = payerMasterRepository.findById(dto.getPayerId()).orElse(null);
        }

        policy.setPayer(payer);
        policy.setPolicyNumber(dto.getPolicyNumber());
        policy.setMemberId(dto.getMemberId());
        policy.setSumInsured(dto.getSumInsured());
        policy.setRoomRentLimit(dto.getRoomRentLimit());
        policy.setCopayPct(dto.getCopayPct());
        policy.setValidFrom(dto.getValidFrom());
        policy.setValidTo(dto.getValidTo());
        policy.setPolicyType(dto.getPolicyType());

        policy = policyRepository.save(policy);
        log.info("Updated InsurancePolicy {}", id);
        return mapToResponse(policy);
    }

    public InsurancePolicyResponse mapToResponse(InsurancePolicy p) {
        return InsurancePolicyResponse.builder()
                .id(p.getId())
                .payerId(p.getPayer() != null ? p.getPayer().getId() : null)
                .insurerName(p.getPayer() != null ? p.getPayer().getInsurerName() : null)
                .tpaName(p.getPayer() != null ? p.getPayer().getTpaName() : null)
                .policyNumber(p.getPolicyNumber())
                .memberId(p.getMemberId())
                .sumInsured(p.getSumInsured())
                .roomRentLimit(p.getRoomRentLimit())
                .copayPct(p.getCopayPct())
                .validFrom(p.getValidFrom())
                .validTo(p.getValidTo())
                .policyType(p.getPolicyType())
                .build();
    }
}
