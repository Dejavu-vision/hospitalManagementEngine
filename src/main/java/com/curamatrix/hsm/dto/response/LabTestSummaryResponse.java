package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabTestSummaryResponse {
    private long totalPending;
    private long totalInProgress;
    private long totalCompleted;
    private long totalCancelled;
}
