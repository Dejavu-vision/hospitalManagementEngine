package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisResponse {
    private Long id;
    private Long appointmentId;
    private Long doctorId;
    private String doctorName;
    private Long patientId;
    private String patientName;
    private String symptoms;
    private String diagnosis;
    private String clinicalNotes;
    private Severity severity;
    private LocalDate followUpDate;
    private List<PrescriptionResponse> prescriptions;
    private LocalDateTime createdAt;
}
