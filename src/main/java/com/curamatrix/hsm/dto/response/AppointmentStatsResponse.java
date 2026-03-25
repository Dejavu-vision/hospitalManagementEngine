package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Appointment statistics report for Hospital Admin.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentStatsResponse {
    private long totalAppointments;
    private long completed;
    private long cancelled;
    private long booked;
    private long inProgress;
    private double completionRate;
    private double cancellationRate;
    private List<Map<String, Object>> byDoctor;
}
