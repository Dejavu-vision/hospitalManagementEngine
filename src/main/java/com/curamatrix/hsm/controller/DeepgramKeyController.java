package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.service.DeepgramKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin-only endpoints for managing the Deepgram API key.
 * Only SUPER_ADMIN role can access these endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/deepgram-key")
@RequiredArgsConstructor
public class DeepgramKeyController {

    private final DeepgramKeyService deepgramKeyService;

    /**
     * GET /api/admin/deepgram-key/status
     * Check the health/status of the current Deepgram API key.
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<DeepgramKeyService.KeyStatus> getKeyStatus() {
        return ResponseEntity.ok(deepgramKeyService.getKeyStatus());
    }

    /**
     * PUT /api/admin/deepgram-key
     * Update the Deepgram API key at runtime — NO restart needed.
     * 
     * Request body: { "apiKey": "your-new-deepgram-key" }
     */
    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> updateApiKey(@RequestBody Map<String, String> body) {
        String newKey = body.get("apiKey");
        if (newKey == null || newKey.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "apiKey is required in the request body"
            ));
        }

        try {
            deepgramKeyService.updateApiKey(newKey);
            DeepgramKeyService.KeyStatus status = deepgramKeyService.getKeyStatus();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Deepgram API key updated successfully. No restart needed!",
                    "keyPreview", status.keyPreview(),
                    "lastUpdated", status.lastUpdated().toString()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
