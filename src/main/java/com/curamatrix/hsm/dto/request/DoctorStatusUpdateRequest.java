package com.curamatrix.hsm.dto.request;

import com.curamatrix.hsm.enums.DoctorStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class DoctorStatusUpdateRequest {
    @NotNull
    private DoctorStatus status;

    /** Optional note — e.g. "Back in 30 min", "Emergency surgery" */
    private String statusNote;

    /** Estimated return time — required when ON_BREAK or IN_SURGERY */
    private LocalTime availableFrom;

    /** Duty start time */
    private LocalTime dutyStart;

    /** Duty end time */
    private LocalTime dutyEnd;
}
