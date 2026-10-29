package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String tokenType;
    private Long userId;
    private String fullName;
    private String role;
    private List<String> roles;
    private Long expiresIn;

    // Multi-tenant fields
    private Long tenantId;
    private String tenantKey;
    private String hospitalName;
    private String logo;
    private String signature;
    private String address;
    private String contactPhone;
    private String contactEmail;
    private String subscriptionPlan;
    private String subscriptionExpiry;
    private String wakeWord;
    private java.util.Map<String, Object> settings;

    // Simplified: only page keys (no permissions)
    private List<String> pageKeys;
}
