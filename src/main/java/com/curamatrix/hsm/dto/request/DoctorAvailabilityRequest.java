package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class DoctorAvailabilityRequest {
    @NotNull
    private LocalDate date;
    @NotNull
    private Boolean isPresent;
}
