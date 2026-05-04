package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.BlockTokenRequest;
import com.curamatrix.hsm.dto.response.BlockedTokenResponse;
import com.curamatrix.hsm.service.BlockedTokenService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/queue/tokens")
@RequiredArgsConstructor
@Tag(name = "5. Queue", description = "Token blocking and reservation management")
public class BlockedTokenController {

    private final BlockedTokenService blockedTokenService;

    /** List all blocked tokens for today (all statuses) */
    @GetMapping("/blocked")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<List<BlockedTokenResponse>> getTodayBlockedTokens() {
        return ResponseEntity.ok(blockedTokenService.getTodayBlockedTokens());
    }

    /** List only BLOCKED (available) tokens for today — used by walk-in form */
    @GetMapping("/blocked/available")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<List<BlockedTokenResponse>> getAvailableBlockedTokens() {
        return ResponseEntity.ok(blockedTokenService.getAvailableBlockedTokens());
    }

    /** Block a token number for today */
    @PostMapping("/block")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<BlockedTokenResponse> blockToken(
            @Valid @RequestBody BlockTokenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(blockedTokenService.blockToken(request));
    }

    /** Release a blocked token back to the pool */
    @DeleteMapping("/blocked/{tokenNumber}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<BlockedTokenResponse> releaseToken(
            @PathVariable Integer tokenNumber) {
        return ResponseEntity.ok(blockedTokenService.releaseToken(tokenNumber));
    }
}
