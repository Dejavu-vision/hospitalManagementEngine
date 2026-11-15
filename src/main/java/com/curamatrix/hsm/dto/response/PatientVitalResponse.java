package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientVitalResponse {
    private Long id;
    private Long appointmentId;
    private BigDecimal temperatureC;
    private Integer bpSystolic;
    private Integer bpDiastolic;
    private Integer pulse;
    private Integer spo2;
    private Integer respiratoryRate;
    private BigDecimal weightKg;
    private BigDecimal heightCm;
    private BigDecimal bmi;
    private BigDecimal bloodSugar;
    private String recordedBy;
    private LocalDateTime recordedAt;
}
