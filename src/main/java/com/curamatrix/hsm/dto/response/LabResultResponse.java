package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabResultResponse {
    private Long id;
    private String parameterName;
    private String resultValue;
    private String unit;
    private String normalRangeLow;
    private String normalRangeHigh;
    private String observations;
    private String enteredByName;
    private LocalDateTime enteredAt;
}
