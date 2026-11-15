package com.curamatrix.hsm.dto.request;

import com.curamatrix.hsm.enums.PatientNoteType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PatientNoteRequest {
    @NotNull(message = "Note type is required")
    private PatientNoteType noteType;

    @NotBlank(message = "Content is required")
    @Size(max = 2000)
    private String content;

    private boolean pinned;
}
