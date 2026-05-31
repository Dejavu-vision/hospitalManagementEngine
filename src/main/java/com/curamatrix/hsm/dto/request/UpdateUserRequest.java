package com.curamatrix.hsm.dto.request;

import com.curamatrix.hsm.enums.RoleName;
import lombok.Data;

@Data
public class UpdateUserRequest {
    private String fullName;
    private String phone;
    private String password;  // optional — only set if non-null

    // Doctor-specific fields (optional — only processed for doctors)
    private String qualification;
    private String licenseNumber;
    private Integer experience;
    private Double consultationFee;  // null = clear override (use catalog rate)
    private Long departmentId;

    // Role change
    private RoleName role;

    // Shift (for receptionist/nurse/lab tech)
    private String shift;

    // Flag to distinguish "field not sent" from "field explicitly set to null"
    private boolean consultationFeeProvided = false;

    public void setConsultationFee(Double consultationFee) {
        this.consultationFee = consultationFee;
        this.consultationFeeProvided = true;
    }
}
