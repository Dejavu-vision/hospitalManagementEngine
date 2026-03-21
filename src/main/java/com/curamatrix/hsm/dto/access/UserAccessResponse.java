package com.curamatrix.hsm.dto.access;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Builder
public class UserAccessResponse {
    private Long userId;
    private String email;
    private Set<String> roles;
    private List<PageAccessDto> pages;
}
