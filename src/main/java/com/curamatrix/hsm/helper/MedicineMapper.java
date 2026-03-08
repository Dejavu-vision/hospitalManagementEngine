package com.curamatrix.hsm.helper;

import com.curamatrix.hsm.dto.MedicineSearchDto;
import com.curamatrix.hsm.entity.Medicine;
import org.springframework.stereotype.Component;

@Component
public class MedicineMapper {

    public MedicineSearchDto toDTO(Medicine medicine) {

        return MedicineSearchDto.builder()
                .id(medicine.getId())
                .name(medicine.getName())
                .strength(medicine.getStrength())
                .form(medicine.getForm())
                .build();
    }
}