package com.curamatrix.hsm.dto.request;

import com.curamatrix.hsm.enums.AdmissionType;
import com.curamatrix.hsm.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdmissionRequest {

    @NotNull(message = "Patient ID is required")
    private Long patientId;

    @NotNull(message = "Primary Doctor ID is required")
    private Long primaryDoctorId;

    private Long opdAppointmentId; // If converting from OPD

    @NotNull(message = "Admission Type is required")
    private AdmissionType admissionType;

    @NotNull(message = "Bed ID is required to admit")
    private Long bedId;

    private String admissionNotes;

    @DecimalMin(value = "0.0", message = "Deposit amount cannot be negative")
    private BigDecimal depositAmount;

    private PaymentMethod paymentMethod;

    private Long preAuthId; // Optional — links pre-auth to admission after creation

    private LocalDateTime expectedDischargeTime; // Optional
}
