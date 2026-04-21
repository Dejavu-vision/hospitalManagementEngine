package com.curamatrix.hsm.dto.request;

import com.curamatrix.hsm.enums.AdmissionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

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
    
    private Double depositAmount;
}
