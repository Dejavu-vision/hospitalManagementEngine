package com.curamatrix.hsm.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class VitalSignResponse {
    private Long id;
    private Integer bpSystolic;
    private Integer bpDiastolic;
    private Integer heartRate;
    private BigDecimal temperature;
    private Integer spO2;
    private Integer respiratoryRate;
    private LocalDateTime recordedAt;
    private String recordedByName;
    private String recordedByRole;
}
