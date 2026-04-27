package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateCheckResponse {
    private boolean exists;
    private List<PatientResponse> patients; // Changed from single patient to list
    @com.fasterxml.jackson.annotation.JsonProperty("isCasePaperValid")
    private boolean isCasePaperValid;
    private String expiresAt;
}
