package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RespondDiscountRequest {

    @NotNull(message = "Approval status is required")
    private Boolean approved;

    private String feedback;
}
