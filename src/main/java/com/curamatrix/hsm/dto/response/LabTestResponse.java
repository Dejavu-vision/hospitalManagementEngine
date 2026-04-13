package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.ServiceCategory;
import com.curamatrix.hsm.enums.TestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabTestResponse {
    private Long id;
    private Long labServiceId;
    private String labServiceName;
    private ServiceCategory category;
    private TestStatus status;
    private LocalDate testDate;
    private String notes;
    private BigDecimal billedPrice;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String cancellationReason;
    private List<LabResultResponse> results;
    private List<LabDocumentResponse> documents;
}
