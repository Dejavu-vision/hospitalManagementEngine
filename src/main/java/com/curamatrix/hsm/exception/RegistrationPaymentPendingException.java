package com.curamatrix.hsm.exception;

/**
 * Thrown when an appointment/token is requested for a patient who has not paid their registration fee.
 * Maps to HTTP 403 Forbidden.
 */
public class RegistrationPaymentPendingException extends RuntimeException {
    public RegistrationPaymentPendingException(String message) {
        super(message);
    }
}
