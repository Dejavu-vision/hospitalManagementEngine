package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Platform-wide summary for Super Admin reports.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSummaryResponse {
    private long totalTenants;
    private long activeHospitals;
    private long suspendedHospitals;
    private long totalUsers;
    private long totalPatients;
    private long totalAppointments;
}
