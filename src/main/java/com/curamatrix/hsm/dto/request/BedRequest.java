package com.curamatrix.hsm.dto.request;

import com.curamatrix.hsm.enums.BedStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class BedRequest {
    @NotBlank(message = "Bed number is required")
    private String bedNumber;
    
    @NotNull(message = "Room ID is required")
    private Long roomId;
    
    @NotNull(message = "Status is required")
    private BedStatus status;
    
    private BigDecimal dailyPrice;
}
