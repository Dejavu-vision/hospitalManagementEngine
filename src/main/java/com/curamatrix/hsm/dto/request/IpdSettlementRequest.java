package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class IpdSettlementRequest {

    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.0", message = "Amount cannot be negative")
    private BigDecimal amount;

    @NotBlank(message = "Payment method is required")
    private String paymentMethod; // CASH, CARD, UPI

    private String transactionRef;

    private String remarks;
}
