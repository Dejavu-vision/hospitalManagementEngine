package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.ServiceCategory;
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
public class LabServiceResponse {
    private Long id;
    private String serviceName;
    private String serviceCode;
    private ServiceCategory category;
    private String description;
    private BigDecimal defaultPrice;
    private BigDecimal currentPrice;
    private boolean active;
    private List<PricingTierResponse> pricingTiers;
}
