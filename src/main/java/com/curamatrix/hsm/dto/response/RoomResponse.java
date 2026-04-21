package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.BedType;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RoomResponse {
    private Long id;
    private String roomNumber;
    private Long wardId;
    private String wardName;
    private BedType roomType;
    private String amenities;
    
    // Optional nested
    private List<BedResponse> beds;
}
