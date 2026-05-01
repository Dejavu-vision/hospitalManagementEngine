package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.BloodGroup;
import com.curamatrix.hsm.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientResponse {
    private Long id;
    private String patientCode;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private Gender gender;
    private String phone;
    private String email;
    private String address;
    private BloodGroup bloodGroup;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String guardianName;
    private String allergies;
    private String medicalHistory;
    private LocalDateTime registeredAt;
    private Boolean checkedIn;
    private Boolean checkedOut;
    private Long activeAppointmentId;
    private Long activeAppointmentDoctorId;
    private String activeAppointmentDoctorName;
    private Integer activeTokenNumber;
    private String activeAppointmentStatus;
    private Boolean casePaperValid;
    private String casePaperExpiresAt;
    private String insuranceProvider;
    private String insurancePolicyNumber;
    private java.util.List<InsurancePolicyResponse> insurancePolicies;
    private String financialStatus;
    private java.math.BigDecimal currentOutstanding;
}
