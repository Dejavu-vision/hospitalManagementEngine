package com.curamatrix.hsm.dto.access;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PageResponse {
    private String pageKey;
    private String route;
    private String displayName;
    private Boolean active;
}
