package com.curamatrix.hsm.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BlockedTokenResponse {
    private Long id;
    private Integer tokenNumber;
    private String tokenDisplay;        // e.g. "T-002"
    private LocalDate appointmentDate;
    private String status;              // BLOCKED | ASSIGNED | RELEASED
    private String reason;
    private String blockedByName;
    private String blockedAt;           // formatted string
    private Long assignedToAppointmentId;
    private String assignedAt;          // formatted string
}
