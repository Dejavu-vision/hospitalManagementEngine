package com.curamatrix.hsm.dto.response;

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
public class PricingTierResponse {
    private Long id;
    private String tierName;
    private BigDecimal price;
    private LocalDate validFrom;
    private LocalDate validTo;
}
