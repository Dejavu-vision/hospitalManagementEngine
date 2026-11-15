package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Everything that happened at one visit. Clinical parts (consultation,
 * prescriptions, lab results) are null/empty for non-clinical callers;
 * the bill is null for doctors.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientVisitDetailResponse {

    private String viewRole;
    private boolean clinicalVisible;

    private Visit visit;
    private Consultation consultation;
    private List<PatientVitalResponse> vitals;
    private List<Medication> prescriptions;
    private List<LabOrder> labOrders;
    private Bill bill;
    private FollowUp followUp;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Visit {
        private Long appointmentId;
        private LocalDate date;
        private LocalTime time;
        private String type;
        private String status;
        private Integer tokenNumber;
        private String doctorName;
        private String department;
        private String doctorQualification;
        private LocalDateTime checkedInAt;
        private LocalDateTime consultationStart;
        private LocalDateTime consultationEnd;
        private Long durationMinutes;
        private String bookingNotes;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Consultation {
        private String symptoms;
        private String diagnosis;
        private String severity;
        private String clinicalNotes;
        /** Tests / investigations the doctor advised (free text). */
        private String investigationsAdvised;
        private String temperature;
        private String bloodPressure;
        private String weight;
        private String doctorName;
        private LocalDateTime recordedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Medication {
        private String medicine;
        private String genericName;
        private String strength;
        private String form;
        private String dosage;
        private String frequency;
        private Integer durationDays;
        private String instructions;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LabOrder {
        private Long labPrescriptionId;
        private String orderedBy;
        private LocalDateTime orderedAt;
        /** False when the order was registered at the front desk and matched by date only. */
        private boolean linkedToVisit;
        private List<LabTestItem> tests;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LabTestItem {
        private Long id;
        private String testName;
        private String category;
        private String status;
        private LocalDate testDate;
        private LocalDateTime completedAt;
        private String notes;
        private String cancellationReason;
        /** Empty for non-clinical callers. */
        private List<LabResultItem> results;
        private List<LabDocumentItem> documents;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LabResultItem {
        private String parameter;
        private String value;
        private String unit;
        private String rangeLow;
        private String rangeHigh;
        /** HIGH, LOW, NORMAL or null when not comparable. */
        private String flag;
        private String observations;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LabDocumentItem {
        private Long id;
        private String fileName;
        private String fileType;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Bill {
        private Long billingId;
        private String invoiceNumber;
        private String paymentStatus;
        private BigDecimal total;
        private BigDecimal discount;
        private BigDecimal net;
        private BigDecimal paid;
        private BigDecimal balance;
        private List<BillItem> items;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BillItem {
        private String description;
        private String type;
        private Integer quantity;
        private BigDecimal amount;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FollowUp {
        private LocalDate date;
        private long daysFromToday;
    }
}
