package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintPreviewResponse {
    private String hospitalName;
    private String doctorName;
    private String doctorSpecialization;
    private String patientName;
    private String patientCode;
    private LocalDate consultationDate;
    private String temperature;
    private String bloodPressure;
    private String weight;
    private String symptoms;
    private String investigations;
    private String diagnosis;
    private String clinicalNotes;
    private Severity severity;
    private LocalDate followUpDate;
    private List<PrescriptionResponse> prescriptions;
}
