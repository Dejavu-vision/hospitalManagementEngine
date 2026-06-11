package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.BedStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableBedSummaryResponse {
    private Long bedId;
    private String bedNumber;
    private BedStatus status;
    private BigDecimal dailyPrice;
}

