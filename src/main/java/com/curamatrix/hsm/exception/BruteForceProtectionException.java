package com.curamatrix.hsm.exception;

/**
 * Thrown when a login attempt is blocked due to too many failed attempts.
 * Maps to HTTP 429 Too Many Requests.
 */
public class BruteForceProtectionException extends RuntimeException {

    public BruteForceProtectionException(String message) {
        super(message);
    }

    public BruteForceProtectionException() {
        super("Too many failed login attempts. Your account is temporarily locked. Please try again after 10 minutes.");
    }
}
