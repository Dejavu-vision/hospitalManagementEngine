package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CasePaperResponse {
    private Long registrationId;
    private Long billingId;
    private String invoiceNumber;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private BigDecimal amount;
    private PaymentStatus paymentStatus;
}
