package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class IpdChargeRequest {

    @NotBlank(message = "Description is required")
    private String description;

    @NotBlank(message = "Charge category is required")
    private String chargeCategory; // Maps to BillingItemType

    @NotNull(message = "Unit price is required")
    @DecimalMin(value = "0.01", message = "Unit price must be positive")
    private BigDecimal unitPrice;

    @NotNull(message = "Quantity is required")
    private Integer quantity;

    private LocalDate chargeDate; // defaults to today if null

    private Long departmentId;
    private Long serviceCatalogItemId;

    private Boolean payNow;
    private String paymentMethod;
    private BigDecimal paidAmount;
}
