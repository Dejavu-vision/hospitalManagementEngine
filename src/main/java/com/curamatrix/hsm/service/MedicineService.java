package com.curamatrix.hsm.service;

import com.curamatrix.hsm.dto.MedicineSearchDto;
import com.curamatrix.hsm.entity.Medicine;
import com.curamatrix.hsm.helper.MedicineMapper;
import com.curamatrix.hsm.repository.MedicineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicineService {

    private final MedicineRepository medicineRepository;
    private final MedicineMapper medicineMapper;

    private static final int MAX_SEARCH_RESULTS = 50;
    private static final int MIN_QUERY_LENGTH = 2;

    /**
     * Fast and efficient medicine search.
     * Searches across name, generic_name, and brand fields.
     * Returns top 10 results ordered by relevance (name matches first).
     * 
     * @param query Search term (minimum 2 characters)
     * @return List of matching medicines
     */
    public List<MedicineSearchDto> searchMedicines(String query) {
        // Validate query
        if (!StringUtils.hasText(query) || query.trim().length() < MIN_QUERY_LENGTH) {
            log.debug("Query too short or empty: '{}'", query);
            return Collections.emptyList();
        }

        String trimmedQuery = query.trim();
        log.debug("Searching medicines with query: '{}'", trimmedQuery);

        try {
            List<Medicine> medicines = medicineRepository.searchMedicines(
                    trimmedQuery,
                    PageRequest.of(0, MAX_SEARCH_RESULTS)
            );

            log.debug("Found {} medicines matching '{}'", medicines.size(), trimmedQuery);

            return medicines.stream()
                    .map(medicineMapper::toDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error searching medicines with query: '{}'", trimmedQuery, e);
            return Collections.emptyList();
        }
    }
}