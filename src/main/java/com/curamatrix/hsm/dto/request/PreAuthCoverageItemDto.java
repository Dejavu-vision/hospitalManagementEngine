package com.curamatrix.hsm.dto.request;

import com.curamatrix.hsm.enums.BillingItemType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PreAuthCoverageItemDto {
    private BillingItemType itemType;
    private BigDecimal approvedAmount;
    private BigDecimal dailyLimit; // null for one-time items like SURGERY
    private String remarks;
}
