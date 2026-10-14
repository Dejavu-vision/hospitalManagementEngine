package com.curamatrix.hsm.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantRenewalRequest {

    /**
     * Target subscription plan: BASIC, STANDARD, PROFESSIONAL, PREMIUM, ENTERPRISE.
     * Optional; defaults to tenant's current plan if null or blank.
     */
    private String subscriptionPlan;

    /**
     * Duration in months to extend (e.g. 1, 3, 6, 12, 24).
     * Defaults to 12 if subscriptionEnd is not explicitly provided.
     */
    private Integer extensionMonths;

    /**
     * Optional new subscription start date.
     * If not provided, retains existing start date (or today if subscription was already expired).
     */
    private LocalDate subscriptionStart;

    /**
     * Optional explicit end date. If provided, overrides extensionMonths.
     */
    private LocalDate subscriptionEnd;

    /**
     * Optional recorded payment amount.
     */
    private BigDecimal amountPaid;

    /**
     * Optional payment reference or receipt/transaction ID.
     */
    private String paymentReference;

    /**
     * Optional notes or remarks regarding the renewal.
     */
    private String notes;

    /**
     * Automatically reactivate tenant if currently suspended or expired.
     * Defaults to true.
     */
    @Builder.Default
    private Boolean autoActivate = true;
}
