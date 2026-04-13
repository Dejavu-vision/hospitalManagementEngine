package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LabPrescriptionItemRequest {
    @NotNull(message = "Lab service ID is required")
    private Long labServiceId;

    private String notes;
}
