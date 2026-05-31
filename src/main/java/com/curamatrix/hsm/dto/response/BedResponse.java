package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.BedStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BedResponse {
    private Long id;
    private String bedNumber;
    private Long roomId;
    private String roomNumber;
    private Long wardId;
    private String wardName;
    private BedStatus status;
}
