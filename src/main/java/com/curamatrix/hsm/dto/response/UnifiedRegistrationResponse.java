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
public class UnifiedRegistrationResponse {
    // Patient Info
    private Long patientId;
    private String patientName;
    private PatientResponse patient;
    
    // Token Info
    private Long appointmentId;
    private Integer tokenNumber;
    private String doctorName;
    private String qualification;
    private String departmentName;
    private Integer activeQueueLength;
    
    // Billing Info
    private Long billingId;
    private String invoiceNumber;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;
    private String paymentStatus;
    
    // Case Paper Info
    private Long registrationId;
    private LocalDateTime casePaperExpiresAt;
}
