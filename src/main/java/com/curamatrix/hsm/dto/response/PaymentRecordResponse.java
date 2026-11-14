package com.curamatrix.hsm.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRecordResponse {
    private Long id;
    private String receiptNumber;
    private BigDecimal amount;
    private String paymentMethod;
    private String referenceNumber;
    private LocalDateTime paymentDate;
}

