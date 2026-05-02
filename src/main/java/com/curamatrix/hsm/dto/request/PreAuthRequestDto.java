package com.curamatrix.hsm.dto.request;

import com.curamatrix.hsm.enums.PreAuthStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PreAuthRequestDto {
    private Long patientId;
    private Long insurancePolicyId;
    private Long admissionId;
    private Long appointmentId;

    // Claim type: "CASHLESS" or "REIMBURSEMENT"
    private String claimType;

    // Clinical coding
    private String diagnosisCode;
    private String procedureCode;

    // Amounts
    private BigDecimal estimatedAmount;
    private BigDecimal approvedAmount;
    private BigDecimal finalClaimAmount;

    // Status
    private PreAuthStatus status;
    private String tpaReferenceNumber;
    private String remarks;
    private String queryResponse;

    // Enhancement
    private Boolean isEnhancement;
    private Long parentPreAuthId;

    // Category-wise approved amounts (filled when TPA approves)
    private List<PreAuthCoverageItemDto> coverageItems;
}
