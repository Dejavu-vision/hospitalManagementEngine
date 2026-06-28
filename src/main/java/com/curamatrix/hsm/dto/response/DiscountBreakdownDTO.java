package com.curamatrix.hsm.dto.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

/**
 * DTO for department-wise discount breakdown in billing responses.
 * Represents a single department's discount amount.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountBreakdownDTO {

    /**
     * Name of the department that received the discount
     */
    @NotBlank(message = "Department name is required")
    private String departmentName;

    /**
     * Discount amount applied to this department
     */
    @NotNull(message = "Discount amount is required")
    private BigDecimal discountAmount;
}
