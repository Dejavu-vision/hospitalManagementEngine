package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.BedType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomWithBedsResponse {
    private Long roomId;
    private String roomNumber;
    private BedType roomType;
    private String amenities;
    private List<AvailableBedSummaryResponse> beds;
}
