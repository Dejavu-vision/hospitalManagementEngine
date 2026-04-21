package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddProgressNoteRequest {
    @NotBlank(message = "Subjective details are required")
    private String subjective;
    
    @NotBlank(message = "Objective details are required")
    private String objective;
    
    @NotBlank(message = "Assessment details are required")
    private String assessment;
    
    @NotBlank(message = "Plan details are required")
    private String plan;
}
