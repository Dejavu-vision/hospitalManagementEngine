package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.PreAuthStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PreAuthResponseDto {
    private Long id;
    private Long patientId;
    private String patientName;
    private String patientCode;
    private Long insurancePolicyId;
    private String policyNumber;
    private String insurerName;
    private String tpaName;
    private Long admissionId;
    private Long appointmentId;

    // Claim type
    private String claimType;

    // Clinical coding
    private String diagnosisCode;
    private String procedureCode;

    // Amounts
    private BigDecimal estimatedAmount;
    private BigDecimal approvedAmount;
    private BigDecimal finalClaimAmount;
    private BigDecimal finalSettledAmount;

    // Status
    private PreAuthStatus status;
    private String tpaReferenceNumber;
    private String remarks;
    private String queryResponse;

    // Enhancement
    private Boolean isEnhancement;
    private Long parentPreAuthId;

    // Category-wise breakdown
    private List<PreAuthCoverageItemResponse> coverageItems;

    // Timestamps
    private LocalDateTime requestedAt;
    private LocalDateTime resolvedAt;
}
