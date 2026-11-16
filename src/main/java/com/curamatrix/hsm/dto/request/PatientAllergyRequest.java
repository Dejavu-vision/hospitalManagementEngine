package com.curamatrix.hsm.dto.request;

import com.curamatrix.hsm.enums.AllergySeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PatientAllergyRequest {
    @NotBlank(message = "Allergen is required")
    @Size(max = 150)
    private String allergen;

    @Size(max = 255)
    private String reaction;

    @NotNull(message = "Severity is required")
    private AllergySeverity severity;
}
