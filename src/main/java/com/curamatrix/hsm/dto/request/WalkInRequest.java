package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WalkInRequest {
    @NotNull(message = "Patient ID is required")
    private Long patientId;

    @NotNull(message = "Doctor ID is required")
    private Long doctorId;

    private String notes;

    private boolean payNow;
}
