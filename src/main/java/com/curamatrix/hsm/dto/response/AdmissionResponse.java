package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.AdmissionStatus;
import com.curamatrix.hsm.enums.AdmissionType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AdmissionResponse {
    private Long id;
    private String admissionNumber;
    private Long patientId;
    private String patientName;
    private Long primaryDoctorId;
    private String primaryDoctorName;
    private Long opdAppointmentId;
    private AdmissionType admissionType;
    private AdmissionStatus status;
    private LocalDateTime admissionTime;
    
    // Current bed details
    private Long currentBedId;
    private String currentBedNumber;
    private String currentWardName;
}
