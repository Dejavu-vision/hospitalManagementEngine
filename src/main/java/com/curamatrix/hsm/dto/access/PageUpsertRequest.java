package com.curamatrix.hsm.dto.access;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PageUpsertRequest {
    @NotBlank
    private String pageKey;
    @NotBlank
    private String route;
    @NotBlank
    private String displayName;
}
