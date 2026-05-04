package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BlockTokenRequest {

    @NotNull(message = "Token number is required")
    @Min(value = 1, message = "Token number must be at least 1")
    private Integer tokenNumber;

    /** Optional reason — e.g. "VIP Reserve", "Emergency slot" */
    private String reason;
}
