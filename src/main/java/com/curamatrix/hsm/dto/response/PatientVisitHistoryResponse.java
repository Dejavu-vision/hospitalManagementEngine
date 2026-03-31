package com.curamatrix.hsm.dto.response;

import lombok.*;

import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PatientVisitHistoryResponse {
    private Long patientId;
    private Long totalVisits;
    private LocalDate lastVisitDate;
}
