package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpdBookingContextResponse {
    private List<DoctorSummary> doctors;
    private List<WardWithBedsResponse> wards;
    private List<PolicySummaryResponse> insurancePolicies;
    private List<AppointmentSummaryResponse> recentAppointments;
}
