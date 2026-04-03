package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationResponse {
    private DiagnosisResponse diagnosis;
    private List<PrescriptionResponse> prescriptions;
    private Long appointmentId;
    private String appointmentStatus;
}
