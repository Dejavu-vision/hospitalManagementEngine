package com.curamatrix.hsm.enums;

import java.util.Set;

public enum AppointmentStatus {
    BOOKED,
    CHECKED_IN,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    /**
     * Defines the valid state transitions per the product guide:
     * BOOKED → CHECKED_IN, CANCELLED
     * CHECKED_IN → IN_PROGRESS, CANCELLED
     * IN_PROGRESS → COMPLETED
     * COMPLETED → (terminal)
     * CANCELLED → (terminal)
     */
    public boolean canTransitionTo(AppointmentStatus target) {
        if (this == target) {
            return false; // No self-transitions
        }
        return switch (this) {
            case BOOKED -> target == CHECKED_IN || target == CANCELLED;
            case CHECKED_IN -> target == IN_PROGRESS || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED;
            case COMPLETED, CANCELLED -> false; // Terminal states
        };
    }
}
