package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.MedicineSearchDto;
import com.curamatrix.hsm.service.MedicineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Medicine Search API.
 * Provides fast autocomplete search for medicines.
 * 
 * Access: ROLE_DOCTOR, ROLE_ADMIN
 * 
 * Endpoint: GET /api/medicines/search?query=parac
 */
@Slf4j
@RestController
@RequestMapping("/api/medicines")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "7. Medicine Search", description = "Fast medicine search and autocomplete (Doctor/Admin)")
public class MedicineController {

    private final MedicineService medicineService;

    /**
     * GET /api/medicines/search?query=parac
     * 
     * Fast autocomplete search for medicines.
     * Searches across name, generic_name, and brand fields.
     * Returns top 10 matches ordered by relevance.
     * 
     * Query parameter validation:
     * - Required: yes
     * - Minimum length: 2 characters
     * 
     * @param query Search term (minimum 2 characters)
     * @return List of matching medicines (empty list if query is invalid or no
     *         matches)
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN','ROLE_SUPER_ADMIN')")
    public ResponseEntity<List<MedicineSearchDto>> searchMedicines(
            @RequestParam("query") String query) {
        log.info("Medicine search request: query='{}'", query);

        // Validate query parameter
        if (!StringUtils.hasText(query) || query.trim().length() < 2) {
            log.warn("Invalid query parameter: '{}' (must be at least 2 characters)", query);
            return ResponseEntity.badRequest().build();
        }

        List<MedicineSearchDto> results = medicineService.searchMedicines(query);

        log.debug("Returning {} results for query '{}'", results.size(), query);

        return ResponseEntity.ok(results);
    }
}
