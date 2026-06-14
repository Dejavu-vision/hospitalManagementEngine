package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SectionDiscountRequest {

    @NotBlank(message = "Section key is required")
    private String section;

    @NotNull(message = "Discount amount is required")
    @PositiveOrZero(message = "Discount must be positive or zero")
    private BigDecimal discount;

    private Long targetAdminId;
}
