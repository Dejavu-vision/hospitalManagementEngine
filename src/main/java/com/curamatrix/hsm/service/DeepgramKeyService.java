package com.curamatrix.hsm.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the Deepgram API key at runtime.
 * - Loads initial key from application.yml / DEEPGRAM_API_KEY env var.
 * - Allows hot-swapping the key via admin endpoint (no restart needed).
 * - Tracks key health status to detect expired/invalid keys.
 */
@Slf4j
@Service
public class DeepgramKeyService {

    @Value("${deepgram.api.key:}")
    private String initialKey;

    private final AtomicReference<String> currentKey = new AtomicReference<>("");
    private volatile LocalDateTime lastUpdated;
    private volatile LocalDateTime lastSuccessfulUse;
    private volatile LocalDateTime lastFailedUse;
    private volatile String lastFailureReason;
    private volatile int consecutiveFailures = 0;

    @PostConstruct
    public void init() {
        if (initialKey != null && !initialKey.trim().isEmpty()) {
            currentKey.set(initialKey.trim());
            lastUpdated = LocalDateTime.now();
            log.info("Deepgram API key loaded from configuration (length: {})", initialKey.trim().length());
        } else {
            log.warn("⚠️ No Deepgram API key configured! Set DEEPGRAM_API_KEY environment variable or update via admin API.");
        }
    }

    /**
     * Get the current active API key.
     */
    public String getApiKey() {
        return currentKey.get();
    }

    /**
     * Check if a valid key is configured.
     */
    public boolean isKeyConfigured() {
        String key = currentKey.get();
        return key != null && !key.trim().isEmpty();
    }

    /**
     * Update the API key at runtime — NO restart needed.
     * @param newKey the new Deepgram API key
     */
    public void updateApiKey(String newKey) {
        if (newKey == null || newKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be empty");
        }
        String trimmed = newKey.trim();
        String oldKeyPreview = maskKey(currentKey.get());
        currentKey.set(trimmed);
        lastUpdated = LocalDateTime.now();
        consecutiveFailures = 0;
        lastFailedUse = null;
        lastFailureReason = null;
        log.info("✅ Deepgram API key updated successfully! Old: {}, New: {} (length: {})",
                oldKeyPreview, maskKey(trimmed), trimmed.length());
    }

    /**
     * Record a successful transcription connection.
     */
    public void recordSuccess() {
        lastSuccessfulUse = LocalDateTime.now();
        consecutiveFailures = 0;
    }

    /**
     * Record a failed transcription connection (e.g., auth error from Deepgram).
     */
    public void recordFailure(String reason) {
        lastFailedUse = LocalDateTime.now();
        lastFailureReason = reason;
        consecutiveFailures++;

        if (consecutiveFailures >= 3) {
            log.error("🚨 DEEPGRAM KEY ALERT: {} consecutive failures! Reason: {}. " +
                    "The API key may be expired or invalid. " +
                    "Update it via PUT /api/admin/deepgram-key or set the DEEPGRAM_API_KEY environment variable.",
                    consecutiveFailures, reason);
        } else {
            log.warn("⚠️ Deepgram connection failed (attempt {}): {}", consecutiveFailures, reason);
        }
    }

    /**
     * Get key health status for the admin dashboard.
     */
    public KeyStatus getKeyStatus() {
        return new KeyStatus(
                isKeyConfigured(),
                maskKey(currentKey.get()),
                lastUpdated,
                lastSuccessfulUse,
                lastFailedUse,
                lastFailureReason,
                consecutiveFailures
        );
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 8) return "NOT_SET";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    /**
     * DTO for key health status.
     */
    public record KeyStatus(
            boolean configured,
            String keyPreview,
            LocalDateTime lastUpdated,
            LocalDateTime lastSuccessfulUse,
            LocalDateTime lastFailedUse,
            String lastFailureReason,
            int consecutiveFailures
    ) {}
}
