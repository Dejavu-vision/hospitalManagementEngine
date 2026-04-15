package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddBillingItemRequest {

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    @NotBlank(message = "Item type is required")
    private String itemType; // CONSULTATION, LAB, MEDICINE, PROCEDURE, REGISTRATION, OTHER
}
