package com.curamatrix.hsm.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MedicineSearchDto {

    private Long id;
    private String name;
    private String strength;
    private String form;
}