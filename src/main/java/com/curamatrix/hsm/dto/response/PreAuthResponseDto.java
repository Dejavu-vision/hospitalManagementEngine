package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.PreAuthStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PreAuthResponseDto {
    private Long id;
    private Long patientId;
    private String patientName;
    private String patientCode;
    private Long insurancePolicyId;
    private String policyNumber;
    private String insurerName;
    private Long admissionId;
    private Long appointmentId;
    private BigDecimal estimatedAmount;
    private BigDecimal approvedAmount;
    private PreAuthStatus status;
    private String tpaReferenceNumber;
    private String remarks;
    private LocalDateTime requestedAt;
    private LocalDateTime resolvedAt;
}
