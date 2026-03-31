package com.curamatrix.hsm.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DashboardStatsResponse {
    private LocalDate date;
    private Long totalBooked;
    private Long totalCheckedIn;
    private Long totalInProgress;
    private Long totalCompleted;
    private Long totalCancelled;
    private Long totalNoShow;
    private List<QueueEntryResponse> waitingPatients;
    private List<QueueEntryResponse> alertPatients;
    private List<DoctorQueueSummary> topDoctors;
}
