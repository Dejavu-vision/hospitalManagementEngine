package com.curamatrix.hsm.dto.request;

import com.curamatrix.hsm.enums.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class DiagnosisRequest {
    @NotNull(message = "Appointment ID is required")
    private Long appointmentId;

    @NotBlank(message = "Symptoms are required")
    private String symptoms;

    @NotBlank(message = "Diagnosis is required")
    private String diagnosis;

    private String clinicalNotes;
    private Severity severity;
    private LocalDate followUpDate;
}
