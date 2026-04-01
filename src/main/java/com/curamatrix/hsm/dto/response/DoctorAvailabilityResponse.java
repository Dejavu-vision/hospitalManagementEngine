package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.DoctorStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DoctorAvailabilityResponse {
    private Long doctorId;
    private String doctorName;
    private LocalDate date;
    private Boolean isPresent;
    private DoctorStatus status;
    private String statusNote;
    private LocalTime availableFrom;
    private LocalTime dutyStart;
    private LocalTime dutyEnd;
}
