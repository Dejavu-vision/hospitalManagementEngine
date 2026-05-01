package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentContextDto {
    private Long patientId;
    private String patientName;
    private List<BillContext> bills;
    private BigDecimal totalBalanceDue;
    private InsuranceProfileContext insuranceProfile;
    private String entryPoint; // "new_registration" | "pending_bill" | "follow_up" | "discharge"
    private Long collectedBy; // staff ID

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillContext {
        private Long billId;
        private String visitDate;
        private String description;
        private BigDecimal grossAmount;
        private BigDecimal insuranceAdjustment;
        private BigDecimal netPayable;
        private BigDecimal amountAlreadyPaid;
        private BigDecimal balanceDue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsuranceProfileContext {
        private String provider;
        private String policyNumber;
        private String tpaName;
        private Boolean cashlessEligible;
        private BigDecimal copayPercentage;
        private String preAuthStatus; // approved / pending / not_applicable
    }
}
