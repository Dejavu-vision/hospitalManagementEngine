package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.AppointmentStatus;
import com.curamatrix.hsm.enums.AppointmentType;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QueueEntryResponse {
    private Long appointmentId;
    private Long patientId;
    private String patientName;
    private String patientCode;
    private Long doctorId;
    private String doctorName;
    private Integer tokenNumber;
    private LocalTime appointmentTime;
    private AppointmentStatus status;
    private AppointmentType type;
    private LocalDateTime checkedInAt;
    private Integer queuePosition;
    private Integer estimatedWaitMinutes;
}
