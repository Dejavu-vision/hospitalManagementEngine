package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.DoctorStatus;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Rich dashboard response powering the new Queue Management UI.
 * Contains all data needed to render the full page in a single API call.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QueueDashboardResponse {

    private LocalDate date;

    // ── Top stat cards ────────────────────────────────────────────────────────
    private Long totalTokensToday;
    private Long currentlyWaiting;
    private Long servedToday;
    private Long noShows;
    private Integer avgConsultMinutes;
    private Integer targetConsultMinutes;
    private Long tokensDeltaFromYesterday;

    // ── Active queues (left sidebar) ──────────────────────────────────────────
    private List<ActiveQueueSummary> activeQueues;

    // ── Currently serving (centre panel) ─────────────────────────────────────
    private QueueEntryResponse currentlyServing;

    // ── Waiting queue for selected department/doctor ──────────────────────────
    private List<QueueEntryResponse> waitingQueue;
    private String waitingQueueLabel;   // e.g. "Waiting Queue — Cardiology"
    private Integer waitingQueueTotal;

    // ── On-hold patients for selected doctor ──────────────────────────────────
    private List<QueueEntryResponse> heldPatients;  // ON_HOLD entries for selected doctor
    private Long onHoldCount;                        // total ON_HOLD for stat card

    // ── Counter status (right panel) ──────────────────────────────────────────
    private List<CounterStatus> counterStatuses;

    // ── Today's summary (right panel) ─────────────────────────────────────────
    private Long tokensIssued;
    private Long served;
    private Long waiting;
    private Long noShowCount;
    private Integer avgWaitMinutes;
    private Integer longestWaitMinutes;
    private String peakHour;

    // ── Recent activity (right panel) ─────────────────────────────────────────
    private List<RecentActivity> recentActivity;

    // ── SMS alerts config ─────────────────────────────────────────────────────
    private Boolean smsEnabled;
    private Integer smsAlertPosition;
    private Long smsSentToday;

    // ─────────────────────────────────────────────────────────────────────────
    // Nested types
    // ─────────────────────────────────────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ActiveQueueSummary {
        private Long doctorId;
        private Long userId;
        private String doctorName;
        private String qualification;
        private Long departmentId;
        private String departmentName;
        private String counterLabel;        // e.g. "Counter 1"
        private DoctorStatus doctorStatus;
        private String statusLabel;         // "Active", "Busy", "Idle"
        private Integer waitingCount;
        private String currentTokenDisplay; // e.g. "T-042"
        private Integer avgWaitMinutes;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CounterStatus {
        private String counterLabel;        // "COUNTER 1", "LAB COUNTER"
        private String tokenDisplay;        // "T-842", "L-028"
        private String doctorName;
        private String specialty;
        private DoctorStatus doctorStatus;
        private String statusLabel;         // "Active", "Busy", "Idle"
        private Boolean isLabCounter;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RecentActivity {
        private String type;            // "CALLED", "DONE", "NO_SHOW", "PRIORITY", "SMS"
        private String tokenDisplay;
        private String patientName;
        private String detail;          // e.g. "Cardiology · Counter 1"
        private String timeAgo;         // e.g. "10:32 AM"
    }
}
