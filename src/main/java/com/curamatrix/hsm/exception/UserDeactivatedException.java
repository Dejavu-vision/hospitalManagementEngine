package com.curamatrix.hsm.exception;

/**
 * Thrown when a deactivated user attempts to log in.
 * Maps to HTTP 403 Forbidden.
 */
public class UserDeactivatedException extends RuntimeException {

    public UserDeactivatedException(String message) {
        super(message);
    }

    public UserDeactivatedException() {
        super("Your account has been deactivated. Please contact your hospital administrator.");
    }
}
