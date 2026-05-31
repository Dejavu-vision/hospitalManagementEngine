package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DepartmentRequest {

    @NotBlank(message = "Department name is required")
    private String name;

    private String description;

    private Boolean isActive = true;
}
