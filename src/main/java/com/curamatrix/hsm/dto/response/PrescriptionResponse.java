package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionResponse {
    private Long id;
    private Long medicineId;
    private String medicineName;
    private String medicineStrength;
    private String medicineForm;
    private String dosage;
    private String frequency;
    private Integer durationDays;
    private String instructions;
}
