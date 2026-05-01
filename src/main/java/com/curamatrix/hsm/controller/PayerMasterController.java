package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.entity.PayerMaster;
import com.curamatrix.hsm.service.PayerMasterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payers")
@RequiredArgsConstructor
@Tag(name = "Payer Master", description = "Endpoints for fetching Insurance/TPA master data")
public class PayerMasterController {

    private final PayerMasterService payerMasterService;

    @Operation(summary = "Get all payers", description = "Retrieves a list of all globally available Insurers and TPAs")
    @GetMapping
    public ResponseEntity<List<PayerMaster>> getAllPayers() {
        return ResponseEntity.ok(payerMasterService.getAllPayers());
    }
}
