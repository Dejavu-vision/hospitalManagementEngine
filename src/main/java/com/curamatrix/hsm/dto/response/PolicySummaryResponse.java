package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicySummaryResponse {
    private Long policyId;
    private Long payerId;
    private String payerName;
    private String tpaName;
    private String policyNumber;
    private String policyType;
    private BigDecimal sumInsured;
}
