package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateBillingItemAmountRequest {

    @NotNull(message = "Amount is required")
    @PositiveOrZero(message = "Amount must be positive or zero")
    private BigDecimal amount;
}
