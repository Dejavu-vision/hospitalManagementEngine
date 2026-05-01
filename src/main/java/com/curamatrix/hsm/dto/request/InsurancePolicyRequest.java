package com.curamatrix.hsm.dto.request;

import com.curamatrix.hsm.enums.PolicyType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InsurancePolicyRequest {
    private Long payerId;
    private String policyNumber;
    private String memberId;
    private BigDecimal sumInsured;
    private BigDecimal roomRentLimit;
    private BigDecimal copayPct;
    private LocalDate validFrom;
    private LocalDate validTo;
    private PolicyType policyType;
}
