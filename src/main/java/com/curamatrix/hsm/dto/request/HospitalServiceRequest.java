package com.curamatrix.hsm.dto.request;

import com.curamatrix.hsm.enums.BillingItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class HospitalServiceRequest {

    @NotBlank(message = "Service name is required")
    private String serviceName;

    @NotBlank(message = "Service code is required")
    private String serviceCode;

    @NotNull(message = "Price is required")
    private BigDecimal price;

    @NotNull(message = "Item type is required")
    private BillingItemType itemType;

    private boolean active = true;

    private String description;

    private Integer validityPeriodDays;

    private Boolean isInsurancePayable = true;

    private Long departmentId;

    private BigDecimal insuranceRate;

    private BigDecimal gstPercentage = BigDecimal.ZERO;

    @NotNull(message = "Effective from date is required")
    private java.time.LocalDate effectiveFrom;
}
