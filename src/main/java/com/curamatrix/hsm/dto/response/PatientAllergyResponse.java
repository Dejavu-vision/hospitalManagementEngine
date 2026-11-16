package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.AllergySeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientAllergyResponse {
    private Long id;
    private String allergen;
    private String reaction;
    private AllergySeverity severity;
    private String recordedBy;
    private LocalDateTime recordedAt;
}
