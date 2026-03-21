package com.curamatrix.hsm.dto.request;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String fullName;
    private String phone;
    private String password;  // optional — only set if non-null
}
