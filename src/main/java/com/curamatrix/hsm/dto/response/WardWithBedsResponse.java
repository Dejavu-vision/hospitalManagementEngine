package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WardWithBedsResponse {
    private Long wardId;
    private String wardName;
    private String floor;
    private List<RoomWithBedsResponse> rooms;
}
