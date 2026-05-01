package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidatedPaymentRequest {

    @NotNull
    private Long patientId;

    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotNull
    private String method;

    private String referenceNumber;
}
