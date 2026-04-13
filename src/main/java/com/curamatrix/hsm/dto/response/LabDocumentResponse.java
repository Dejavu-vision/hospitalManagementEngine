package com.curamatrix.hsm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabDocumentResponse {
    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String uploadedByName;
    private LocalDateTime uploadedAt;
    private String downloadUrl;
}
