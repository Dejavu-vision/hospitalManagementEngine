package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class WalkInRequest {
    @NotNull(message = "Patient ID is required")
    private Long patientId;

    // Nullable — triggers auto-assign when null (departmentId must be provided instead)
    private Long doctorId;

    // Required when doctorId is null — used for auto-assign
    private Long departmentId;

    private String notes;

    private boolean payNow;
    private boolean followUp;

    @Pattern(regexp = "^[ABC]$", message = "Counter must be A, B, or C")
    private String counter;

    /**
     * Optional — when set, assigns this specific blocked (reserved) token number
     * to the patient instead of auto-incrementing.
     * The token must exist in blocked_tokens with status BLOCKED.
     */
    private Integer blockedTokenNumber;
}
