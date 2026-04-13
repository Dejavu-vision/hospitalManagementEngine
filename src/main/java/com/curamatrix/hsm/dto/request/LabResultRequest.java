package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LabResultRequest {
    private String parameterName;

    @NotBlank(message = "Result value is required")
    private String resultValue;

    private String unit;

    private String normalRangeLow;

    private String normalRangeHigh;

    private String observations;
}
