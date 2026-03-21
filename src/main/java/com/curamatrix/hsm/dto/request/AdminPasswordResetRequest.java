package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminPasswordResetRequest {

    @Email(message = "Invalid admin email format")
    private String adminEmail;

    @NotBlank(message = "New password is required")
    private String newPassword;
}

