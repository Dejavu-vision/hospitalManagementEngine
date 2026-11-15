package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PatientVitalRequest {
    private Long appointmentId;

    @DecimalMin("30.0") @DecimalMax("45.0")
    private BigDecimal temperatureC;

    @Min(40) @Max(300)
    private Integer bpSystolic;

    @Min(20) @Max(200)
    private Integer bpDiastolic;

    @Min(20) @Max(300)
    private Integer pulse;

    @Min(30) @Max(100)
    private Integer spo2;

    @Min(4) @Max(80)
    private Integer respiratoryRate;

    @DecimalMin("0.5") @DecimalMax("500.0")
    private BigDecimal weightKg;

    @DecimalMin("20.0") @DecimalMax("260.0")
    private BigDecimal heightCm;

    @DecimalMin("10.0") @DecimalMax("1500.0")
    private BigDecimal bloodSugar;
}
