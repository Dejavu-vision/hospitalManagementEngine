package com.curamatrix.hsm.exception;

/**
 * Thrown when an invalid state transition is attempted (e.g., appointment status).
 * Maps to HTTP 422 Unprocessable Entity.
 */
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }

    public InvalidStateTransitionException(String entityName, String currentState, String targetState) {
        super(String.format("Invalid %s transition: cannot move from %s to %s", entityName, currentState, targetState));
    }
}
