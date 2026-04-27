package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lightweight patient summary returned by GET /api/patients/search?q=
 * Includes inline case paper status so the receptionist can see validity
 * without a second round-trip.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientSummaryResponse {

    private Long id;
    private String patientCode;
    private String firstName;
    private String lastName;
    private String phone;
    private String dateOfBirth;
    private String gender;
    private LocalDate lastVisitDate;

    /** Backend-computed case paper status — never trust the frontend for this. */
    private CasePaperSummary casePaper;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CasePaperSummary {
        private boolean valid;
        private LocalDateTime expiresAt;
        private long remainingDays;
        /** true when remainingDays <= 5 */
        private boolean expiringSoon;
    }
}
