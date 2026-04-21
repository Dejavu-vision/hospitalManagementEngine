package com.curamatrix.hsm.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class DailyProgressNoteResponse {
    private Long id;
    private String subjective;
    private String objective;
    private String assessment;
    private String plan;
    private LocalDateTime noteTime;
    private String doctorName;
}
