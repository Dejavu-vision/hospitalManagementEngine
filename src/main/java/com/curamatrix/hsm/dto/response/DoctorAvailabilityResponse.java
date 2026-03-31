package com.curamatrix.hsm.dto.response;

import lombok.*;

import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DoctorAvailabilityResponse {
    private Long doctorId;
    private LocalDate date;
    private Boolean isPresent;
}
