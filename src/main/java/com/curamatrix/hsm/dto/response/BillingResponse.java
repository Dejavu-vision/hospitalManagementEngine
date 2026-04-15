package com.curamatrix.hsm.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingResponse {

    private Long id;
    private String invoiceNumber;

    private Long patientId;
    private String patientName;
    private String patientCode;

    private Long appointmentId;

    private BigDecimal totalAmount;
    private BigDecimal discount;
    private BigDecimal tax;
    private BigDecimal netAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;

    private String paymentStatus;
    private String paymentMethod;

    private List<BillingItemResponse> items;

    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private String createdByName;
    private String remarks;
}
