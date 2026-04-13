package com.curamatrix.hsm.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ReceptionistLabRegistrationRequest {
    @NotNull(message = "Patient ID is required")
    private Long patientId;

    @NotEmpty(message = "At least one lab service item is required")
    @Valid
    private List<LabPrescriptionItemRequest> items;
}
