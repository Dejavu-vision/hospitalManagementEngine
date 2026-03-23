package com.curamatrix.hsm.dto.request;

import com.curamatrix.hsm.enums.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class CreateUserRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String phone;

    @NotNull(message = "Role is required")
    private RoleName role;

    // Doctor-specific fields (required if role is ROLE_DOCTOR)
    private Long departmentId;
    private String specialization;
    private String licenseNumber;
    private String qualification;
    private Integer experienceYears;
    private Double consultationFee;

    // Receptionist-specific fields (required if role is ROLE_RECEPTIONIST)
    // Note: employeeId is auto-generated — not accepted from the client
    private String shift;

    // Extra page access (optional — pages beyond what the role provides)
    private Set<String> allowedPageKeys;
}
