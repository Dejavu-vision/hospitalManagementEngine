package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotResponse {
    private Long doctorId;
    private String doctorName;
    private LocalDate date;
    private Integer slotDurationMinutes;
    private List<String> availableSlots;
    private List<String> bookedSlots;
}
