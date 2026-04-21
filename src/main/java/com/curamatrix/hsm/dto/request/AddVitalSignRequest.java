package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class AddVitalSignRequest {
    private Integer bpSystolic;
    private Integer bpDiastolic;
    private Integer heartRate;
    private BigDecimal temperature;
    private Integer spO2;
    private Integer respiratoryRate;
}
