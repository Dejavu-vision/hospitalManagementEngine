package com.curamatrix.hsm.service;

import com.curamatrix.hsm.dto.MedicineSearchDto;
import com.curamatrix.hsm.entity.Medicine;
import com.curamatrix.hsm.helper.MedicineMapper;
import com.curamatrix.hsm.repository.MedicineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicineService {

    private final MedicineRepository medicineRepository;
    private final MedicineMapper medicineMapper;

    public List<MedicineSearchDto> searchMedicines(String query) {

        List<Medicine> medicines =
                medicineRepository.findByNameStartingWithIgnoreCase(
                        query,
                        PageRequest.of(0, 10)
                );

        return medicines.stream()
                .map(medicineMapper::toDTO)
                .collect(Collectors.toList());
    }
}