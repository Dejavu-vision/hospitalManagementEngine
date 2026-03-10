package com.curamatrix.hsm.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TenantRegistrationRequest {
    
    @NotBlank(message = "Tenant key is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Tenant key must be lowercase alphanumeric with hyphens")
    private String tenantKey; // e.g., "apollo-mumbai"

    @NotBlank(message = "Hospital name is required")
    private String hospitalName;

    @NotBlank(message = "Subscription plan is required")
    private String subscriptionPlan; // BASIC, STANDARD, PREMIUM

    @NotNull(message = "Subscription start date is required")
    private LocalDate subscriptionStart;

    @NotNull(message = "Subscription end date is required")
    private LocalDate subscriptionEnd;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Invalid email format")
    private String contactEmail;

    private String contactPhone;
    private String address;
    private String logo;

    // Admin user details (created automatically)
    @NotBlank(message = "Admin full name is required")
    private String adminFullName;

    @NotBlank(message = "Admin email is required")
    @Email(message = "Invalid admin email format")
    private String adminEmail;

    @NotBlank(message = "Admin password is required")
    private String adminPassword;

    private String adminPhone;
}
