package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.ServiceCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabServiceSearchResponse {
    private Long id;
    private String serviceName;
    private String serviceCode;
    private ServiceCategory category;
    private BigDecimal currentPrice;
}
