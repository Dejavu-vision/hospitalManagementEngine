package com.curamatrix.hsm.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingSummaryResponse {

    private long totalInvoices;
    private long pendingCount;
    private long paidCount;
    private long partialCount;

    private BigDecimal totalRevenue;
    private BigDecimal pendingAmount;
    private BigDecimal collectedToday;
}
