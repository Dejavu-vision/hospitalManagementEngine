package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.PatientNoteType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientNoteResponse {
    private Long id;
    private PatientNoteType noteType;
    private String content;
    private boolean pinned;
    private String createdBy;
    private LocalDateTime createdAt;
    /** True when the caller may delete this note. */
    private boolean canDelete;
}
