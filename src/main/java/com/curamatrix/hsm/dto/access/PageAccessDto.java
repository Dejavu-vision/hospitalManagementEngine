package com.curamatrix.hsm.dto.access;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PageAccessDto {
    private String pageKey;
    private String route;
    private String displayName;
}
