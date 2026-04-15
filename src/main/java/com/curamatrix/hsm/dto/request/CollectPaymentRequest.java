package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectPaymentRequest {

    @NotNull(message = "Payment method is required")
    private String paymentMethod; // CASH, CARD, UPI, INSURANCE

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private String remarks;
}
