package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.PolicyType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class InsurancePolicyResponse {
    private Long id;
    private Long payerId;
    private String insurerName;
    private String tpaName;
    private String policyNumber;
    private String memberId;
    private BigDecimal sumInsured;
    private BigDecimal roomRentLimit;
    private BigDecimal copayPct;
    private LocalDate validFrom;
    private LocalDate validTo;
    private PolicyType policyType;
}
