package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WardRequest {
    @NotBlank(message = "Ward name is required")
    private String name;
    
    private String floor;
    private String description;
}
