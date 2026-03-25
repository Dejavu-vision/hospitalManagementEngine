package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Doctor workload report for Hospital Admin.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorWorkloadResponse {
    private int totalDoctors;
    private List<Map<String, Object>> doctorStats;
}
