package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Patient registration trends report for Hospital Admin.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientTrendResponse {
    private long totalPatients;
    private long patientsThisMonth;
    private long patientsThisWeek;
    private List<Map<String, Object>> monthlyTrend;
}
