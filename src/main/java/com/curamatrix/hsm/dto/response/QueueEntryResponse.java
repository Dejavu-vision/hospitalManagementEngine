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
    private String patientAge;          // e.g. "40 yrs / Male"
    private Long doctorId;
    private String doctorName;
    private String doctorQualification;
    private Long departmentId;
    private String departmentName;
    private String counterLabel;        // e.g. "Counter 1"
    private Integer tokenNumber;
    private String tokenDisplay;        // e.g. "T-042"
    private LocalTime appointmentTime;
    private AppointmentStatus status;
    private AppointmentType type;
    private String visitType;           // e.g. "OPD - Follow-up", "New Visit"
    private String priorityCategory;    // "EMERGENCY", "SENIOR_CITIZEN", "PREGNANT_WOMAN", "REGULAR"
    private String priorityLabel;       // "Emergency", "Senior 65+", "Pregnant", "Normal"
    private LocalDateTime checkedInAt;
    private Integer queuePosition;
    private Integer estimatedWaitMinutes;
    private Integer waitingMinutes;     // actual minutes waited so far
    private String registeredAt;        // formatted time string e.g. "10:12 AM"
    private String waitDuration;        // formatted e.g. "28 min"
    private String uhid;                // e.g. "UHID-2026-04821"
    private Integer recallCount;        // number of times this token has been recalled
    private LocalDateTime heldAt;       // populated for ON_HOLD entries
    private Integer holdMinutes;        // minutes elapsed since hold (computed server-side)
    private Boolean casePaperValid;     // whether patient has a valid (active & non-expired) case paper
}
