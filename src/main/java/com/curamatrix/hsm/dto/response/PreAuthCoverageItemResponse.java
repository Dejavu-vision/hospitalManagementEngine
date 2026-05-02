package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.BillingItemType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PreAuthCoverageItemResponse {
    private Long id;
    private BillingItemType itemType;
    private BigDecimal approvedAmount;
    private BigDecimal dailyLimit;
    private String remarks;
}
