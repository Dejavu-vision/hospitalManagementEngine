package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.ServiceCategory;
import com.curamatrix.hsm.enums.TestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabTestDashboardResponse {
    private Long id;
    private String patientName;
    private String patientCode;
    private String labServiceName;
    private ServiceCategory category;
    private TestStatus status;
    private String doctorName;
    private String notes;
    private LocalDateTime createdAt;
}
