package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PricingTierRequest {
    @NotBlank(message = "Tier name is required")
    private String tierName;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be at least 0.01")
    private BigDecimal price;

    @NotNull(message = "Valid from date is required")
    private LocalDate validFrom;

    private LocalDate validTo;
}
