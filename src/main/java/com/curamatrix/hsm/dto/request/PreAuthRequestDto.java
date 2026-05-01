package com.curamatrix.hsm.dto.request;

import com.curamatrix.hsm.enums.PreAuthStatus;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PreAuthRequestDto {
    private Long patientId;
    private Long insurancePolicyId;
    private Long admissionId;
    private Long appointmentId;
    private BigDecimal estimatedAmount;
    private BigDecimal approvedAmount;
    private PreAuthStatus status;
    private String tpaReferenceNumber;
    private String remarks;
}
