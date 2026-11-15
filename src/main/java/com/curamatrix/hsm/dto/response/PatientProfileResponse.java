package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregated, role-aware view of a patient. Sections the caller may not see
 * are returned as null / empty and are not listed in {@code sections}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientProfileResponse {

    /** "DOCTOR", "RECEPTIONIST" or "ADMIN" – drives UI tab visibility. */
    private String viewRole;
    private List<String> sections;

    private Header header;
    private List<Alert> alerts;
    private Demographics demographics;
    private CasePaper casePaper;
    private ActiveVisit activeVisit;
    private Stats stats;
    private List<PatientAllergyResponse> allergies;
    private PatientVitalResponse latestVitals;
    private Financial financial;
    private List<InsurancePolicyResponse> insurance;
    private List<RecentVisit> recentVisits;
    private List<ActiveMedication> activeMedications;
    private List<PatientNoteResponse> pinnedNotes;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Header {
        private Long id;
        private String patientCode;
        private String fullName;
        private Integer age;
        private String gender;
        private String bloodGroup;
        private String phone;
        private LocalDate dateOfBirth;
        private LocalDateTime registeredAt;
        private boolean checkedIn;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Alert {
        /** CRITICAL, WARNING, INFO */
        private String level;
        /** ALLERGY, CASE_PAPER, BALANCE, ADMISSION, FOLLOW_UP, VITALS */
        private String type;
        private String message;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Demographics {
        private String firstName;
        private String lastName;
        private String email;
        private String address;
        private String guardianName;
        private String emergencyContactName;
        private String emergencyContactPhone;
        private String medicalHistory;
        /** Legacy free-text allergies, kept for patients without structured entries. */
        private String legacyAllergies;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CasePaper {
        private boolean exists;
        private boolean valid;
        private LocalDateTime issuedAt;
        private LocalDateTime expiresAt;
        private long remainingDays;
        private boolean expiringSoon;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ActiveVisit {
        private Long appointmentId;
        private String status;
        private Integer tokenNumber;
        private Long doctorId;
        private String doctorName;
        private String department;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Stats {
        private long completedVisits;
        private long totalAppointments;
        private long noShows;
        private LocalDate lastVisitDate;
        private LocalDate nextFollowUpDate;
        private long admissions;
        private boolean currentlyAdmitted;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Financial {
        private BigDecimal totalBilled;
        private BigDecimal totalPaid;
        private BigDecimal outstanding;
        private long pendingInvoices;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RecentVisit {
        private Long appointmentId;
        private LocalDate date;
        private String type;
        private String status;
        private Integer tokenNumber;
        private String doctorName;
        private String department;
        /** Clinical fields: only populated for doctor/admin views. */
        private String diagnosis;
        private String symptoms;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ActiveMedication {
        private String medicine;
        private String dosage;
        private String frequency;
        private Integer durationDays;
        private String instructions;
        private LocalDate prescribedOn;
        private LocalDate endsOn;
        private String prescribedBy;
    }
}
