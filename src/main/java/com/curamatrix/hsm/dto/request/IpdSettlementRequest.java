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

    private String paymentMethod; // CASH, CARD, UPI, NEFT, CHEQUE — optional when balance = 0

    private BigDecimal refundAmount; // positive value when hospital owes patient a refund

    private String transactionRef;

    private String remarks;
}
