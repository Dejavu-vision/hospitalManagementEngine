package com.curamatrix.hsm.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class WardResponse {
    private Long id;
    private String name;
    private String floor;
    private String description;
    
    // Optional nested
    private List<RoomResponse> rooms; 
}
