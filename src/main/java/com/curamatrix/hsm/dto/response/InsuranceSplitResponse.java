package com.curamatrix.hsm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class InsuranceSplitResponse {
    private Long splitId;
    private Long insurancePolicyId;
    private String insurerName;
    private String policyNumber;

    private BigDecimal grossAmount;
    private BigDecimal nonPayableAmount;
    private BigDecimal roomRentDeductible;
    private BigDecimal coveredBase;
    private BigDecimal copayAmount;
    private BigDecimal patientLiability;
    private BigDecimal insuranceClaim;
    private LocalDateTime calculatedAt;
}
