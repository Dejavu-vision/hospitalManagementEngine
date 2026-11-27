package com.curamatrix.hsm.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Public endpoint to verify deployed version and health status.
 */
@RestController
@RequestMapping("/api/public")
public class VersionController {

    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> getVersion() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "app", "CuraMatrix HSM",
                "version", "v1.0.1",
                "labTechRoleSupport", true,
                "timestamp", Instant.now().toString()
        ));
    }
}
