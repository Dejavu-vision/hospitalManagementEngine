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
public class DuplicateCheckResponse {
    private boolean exists;
    private PatientResponse patient;
    @com.fasterxml.jackson.annotation.JsonProperty("isCasePaperValid")
    private boolean isCasePaperValid;
    private String expiresAt;
}
