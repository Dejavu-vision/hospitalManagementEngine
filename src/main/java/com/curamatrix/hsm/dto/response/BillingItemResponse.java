package com.curamatrix.hsm.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingItemResponse {

    private Long id;
    private String description;
    private BigDecimal amount;
    private Integer quantity;
    private String itemType;
    private String insuranceCoverage;
    private BigDecimal subtotal;

    private String paymentStatus;
    private BigDecimal paidAmount;
    private Long departmentId;
    private String departmentName;
    private Long serviceCatalogItemId;
    private java.time.LocalDateTime createdAt;
}
