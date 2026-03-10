package com.curamatrix.hsm.dto.response;

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
public class TenantResponse {
    private Long id;
    private String tenantKey;
    private String hospitalName;
    private String subscriptionPlan;
    private LocalDate subscriptionStart;
    private LocalDate subscriptionEnd;
    private Boolean isActive;
    private Integer maxUsers;
    private Integer maxPatients;
    private String contactEmail;
    private String contactPhone;
    private String address;
    private String logo;
    private LocalDateTime createdAt;
    
    // Usage stats
    private Integer currentUsers;
    private Integer currentPatients;
}
