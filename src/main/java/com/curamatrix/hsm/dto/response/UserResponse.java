package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private Boolean isActive;
    private String role;               // primary role (backward compat)
    private Set<String> roles;         // all role names
    private List<String> pageKeys;     // effective page keys
    private Long tenantId;             // which hospital/tenant this user belongs to
    private LocalDateTime createdAt;
}
