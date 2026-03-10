package com.curamatrix.hsm.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class PrescriptionBatchRequest {
    @NotNull(message = "Diagnosis ID is required")
    private Long diagnosisId;

    @NotEmpty(message = "At least one prescription is required")
    @Valid
    private List<PrescriptionItemRequest> prescriptions;
}
