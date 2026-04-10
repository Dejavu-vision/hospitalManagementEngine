package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.response.BookingContextResponse;
import com.curamatrix.hsm.dto.response.CasePaperResponse;
import com.curamatrix.hsm.service.ReceptionDeskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/reception")
@RequiredArgsConstructor
public class ReceptionDeskController {

    private final ReceptionDeskService receptionDeskService;

    @GetMapping("/booking-context/{patientId}")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<BookingContextResponse> getBookingContext(@PathVariable Long patientId) {
        log.info("Fetching booking context for patient: {}", patientId);
        BookingContextResponse response = receptionDeskService.getBookingContext(patientId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/case-paper/{patientId}")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<CasePaperResponse> createCasePaper(
            @PathVariable Long patientId,
            @RequestParam(required = false) String paymentMethod) {
        log.info("Creating case paper for patient: {}", patientId);
        CasePaperResponse response = receptionDeskService.createCasePaper(patientId, paymentMethod);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
