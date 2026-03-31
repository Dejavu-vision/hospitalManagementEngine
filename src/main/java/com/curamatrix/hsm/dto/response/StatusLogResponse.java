package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.AppointmentStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StatusLogResponse {
    private Long id;
    private AppointmentStatus previousStatus;
    private AppointmentStatus newStatus;
    private String changedByName;
    private LocalDateTime changedAt;
}
