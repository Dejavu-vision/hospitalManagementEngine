package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedRegistrationRequest {
    // Patient Data (Required for new, optional for existing)
    private PatientRequest patient;
    
    // If set, we use this patient and update if needed
    private Long existingPatientId; 
    
    // Visit Data
    @NotNull(message = "Department ID is required")
    private Long departmentId;
    private Long doctorId;
    private boolean followUp;
    private String notes;
    
    // Payment Data
    @NotNull(message = "Payment method is required")
    private String paymentMethod;
    private BigDecimal paidAmount;
    private String paymentRemarks;
    
    // Token Assignment (optional)
    private Integer blockedTokenNumber;
}
