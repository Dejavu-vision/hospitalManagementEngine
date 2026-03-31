package com.curamatrix.hsm.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DoctorWithAvailabilityResponse {
    private Long doctorId;
    private String doctorName;
    private String qualification;
    private BigDecimal consultationFee;
    private Boolean isPresentToday;
}
