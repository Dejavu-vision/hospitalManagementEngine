package com.curamatrix.hsm.dto.request;

import com.curamatrix.hsm.enums.BedType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RoomRequest {
    @NotBlank(message = "Room number is required")
    private String roomNumber;
    
    @NotNull(message = "Ward ID is required")
    private Long wardId;
    
    @NotNull(message = "Room type is required")
    private BedType roomType;
    
    private String amenities;
}
