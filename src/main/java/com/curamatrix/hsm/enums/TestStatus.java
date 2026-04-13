package com.curamatrix.hsm.enums;

public enum TestStatus {
    PENDING, IN_PROGRESS, COMPLETED, CANCELLED;

    public boolean canTransitionTo(TestStatus target) {
        if (this == target) return false;
        return switch (this) {
            case PENDING     -> target == IN_PROGRESS || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false; // terminal states
        };
    }
}
