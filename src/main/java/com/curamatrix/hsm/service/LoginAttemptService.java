package com.curamatrix.hsm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory login attempt tracker for brute-force protection.
 * Locks accounts after MAX_ATTEMPTS failed login attempts within LOCK_DURATION_MINUTES.
 * Auto-unlocks after the lock duration expires.
 *
 * Thread-safe via ConcurrentHashMap — no external dependencies required.
 */
@Slf4j
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 10;
    private static final long LOCK_DURATION_MILLIS = LOCK_DURATION_MINUTES * 60 * 1000;

    private final ConcurrentHashMap<String, AttemptRecord> attemptsMap = new ConcurrentHashMap<>();

    /**
     * Check if the given email is currently blocked due to too many failed attempts.
     */
    public boolean isBlocked(String email) {
        String key = normalizeKey(email);
        AttemptRecord record = attemptsMap.get(key);
        if (record == null) {
            return false;
        }

        // Auto-unlock if lock duration has expired
        if (record.isLocked && Instant.now().toEpochMilli() - record.lockedAt > LOCK_DURATION_MILLIS) {
            attemptsMap.remove(key);
            log.info("Auto-unlocked account after lock duration expired: {}", email);
            return false;
        }

        return record.isLocked;
    }

    /**
     * Record a failed login attempt. If MAX_ATTEMPTS is reached, lock the account.
     */
    public void recordFailure(String email) {
        String key = normalizeKey(email);
        attemptsMap.compute(key, (k, existing) -> {
            if (existing == null) {
                return new AttemptRecord(1, false, 0, Instant.now().toEpochMilli());
            }

            // Reset if the window has expired (old failures don't count)
            if (Instant.now().toEpochMilli() - existing.firstAttemptAt > LOCK_DURATION_MILLIS) {
                return new AttemptRecord(1, false, 0, Instant.now().toEpochMilli());
            }

            int newCount = existing.failedAttempts + 1;
            boolean shouldLock = newCount >= MAX_ATTEMPTS;

            if (shouldLock) {
                log.warn("Account locked due to {} failed login attempts: {}", newCount, email);
            }

            return new AttemptRecord(
                    newCount,
                    shouldLock,
                    shouldLock ? Instant.now().toEpochMilli() : 0,
                    existing.firstAttemptAt
            );
        });
    }

    /**
     * Record a successful login — clears the failure record.
     */
    public void recordSuccess(String email) {
        String key = normalizeKey(email);
        attemptsMap.remove(key);
    }

    /**
     * Get remaining lock time in seconds (0 if not locked).
     */
    public long getRemainingLockSeconds(String email) {
        String key = normalizeKey(email);
        AttemptRecord record = attemptsMap.get(key);
        if (record == null || !record.isLocked) {
            return 0;
        }
        long elapsed = Instant.now().toEpochMilli() - record.lockedAt;
        long remaining = LOCK_DURATION_MILLIS - elapsed;
        return remaining > 0 ? remaining / 1000 : 0;
    }

    private String normalizeKey(String email) {
        return email != null ? email.toLowerCase().trim() : "";
    }

    /**
     * Internal record to track attempts per email.
     */
    private static class AttemptRecord {
        final int failedAttempts;
        final boolean isLocked;
        final long lockedAt;
        final long firstAttemptAt;

        AttemptRecord(int failedAttempts, boolean isLocked, long lockedAt, long firstAttemptAt) {
            this.failedAttempts = failedAttempts;
            this.isLocked = isLocked;
            this.lockedAt = lockedAt;
            this.firstAttemptAt = firstAttemptAt;
        }
    }
}
