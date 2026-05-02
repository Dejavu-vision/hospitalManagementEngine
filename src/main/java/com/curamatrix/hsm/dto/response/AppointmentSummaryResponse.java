package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentSummaryResponse {
    private Long appointmentId;
    private String appointmentDate;
    private String doctorName;
    private String departmentName;
    private String status;
}
