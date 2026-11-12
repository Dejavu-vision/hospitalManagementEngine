package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintPreviewResponse {
    private String hospitalName;
    private String hospitalLogo;
    private String hospitalSignature;
    private String hospitalAddress;
    private String hospitalPhone;
    private String hospitalEmail;
    private String doctorName;
    private String doctorRegNo;
    private String doctorSignature;
    private String doctorSpecialization;
    private String patientName;
    private String patientCode;
    private Integer patientAge;
    private String patientGender;
    private String patientPhone;
    private String patientAddress;
    private String patientBloodGroup;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String allergies;
    private String medicalHistory;
    private Integer tokenNumber;
    private String visitType;
    private String departmentName;
    private String pulse;
    private String spo2;
    private LocalDate validTill;
    private String casePaperNumber;
    private LocalDate consultationDate;
    private String temperature;
    private String bloodPressure;
    private String weight;
    private String symptoms;
    private String investigations;
    private String diagnosis;
    private String clinicalNotes;
    private Severity severity;
    private LocalDate followUpDate;
    private List<PrescriptionResponse> prescriptions;
}
