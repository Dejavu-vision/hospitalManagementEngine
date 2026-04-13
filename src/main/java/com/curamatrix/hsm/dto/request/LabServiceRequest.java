package com.curamatrix.hsm.dto.request;

import com.curamatrix.hsm.enums.ServiceCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LabServiceRequest {
    @NotBlank(message = "Service name is required")
    private String serviceName;

    @NotBlank(message = "Service code is required")
    private String serviceCode;

    @NotNull(message = "Category is required")
    private ServiceCategory category;

    private String description;

    @NotNull(message = "Default price is required")
    @DecimalMin(value = "0.01", message = "Default price must be at least 0.01")
    private BigDecimal defaultPrice;
}
