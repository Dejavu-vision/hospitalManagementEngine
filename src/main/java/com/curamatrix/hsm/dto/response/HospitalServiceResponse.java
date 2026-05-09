package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.BillingItemType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class HospitalServiceResponse {
    private Long id;
    private String serviceName;
    private String serviceCode;
    private BigDecimal price;
    private BillingItemType itemType;
    private boolean active;
    private String description;
    private Integer validityPeriodDays;
    private Boolean isInsurancePayable;
    private Long departmentId;
    private String departmentName;
    private BigDecimal insuranceRate;
    private BigDecimal gstPercentage;
}
