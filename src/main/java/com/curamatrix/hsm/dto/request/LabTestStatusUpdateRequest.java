package com.curamatrix.hsm.dto.request;

import com.curamatrix.hsm.enums.TestStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LabTestStatusUpdateRequest {
    @NotNull(message = "New status is required")
    private TestStatus newStatus;

    private String cancellationReason;
}
