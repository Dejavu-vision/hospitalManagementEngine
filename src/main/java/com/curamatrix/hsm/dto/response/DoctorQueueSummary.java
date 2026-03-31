package com.curamatrix.hsm.dto.response;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DoctorQueueSummary {
    private Long doctorId;
    private String doctorName;
    private int activeQueueLength;
    private int totalToday;
}
