package com.curamatrix.hsm.dto.request;

import com.curamatrix.hsm.enums.Severity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ConsultationSubmitRequest {
    @NotNull(message = "Appointment ID is required")
    private Long appointmentId;

    @NotBlank(message = "Symptoms are required")
    private String symptoms;

    @NotBlank(message = "Diagnosis is required")
    private String diagnosis;

    private String clinicalNotes;
    private Severity severity;
    private LocalDate followUpDate;
    private String temperature;
    private String bloodPressure;
    private String weight;
    private String investigations;

    @Valid
    private List<PrescriptionItemRequest> prescriptions;
}
