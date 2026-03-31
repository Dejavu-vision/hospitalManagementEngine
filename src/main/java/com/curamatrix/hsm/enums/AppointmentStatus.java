package com.curamatrix.hsm.enums;

public enum AppointmentStatus {
    BOOKED, CHECKED_IN, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW;

    public boolean canTransitionTo(AppointmentStatus target) {
        if (this == target) return false;
        return switch (this) {
            case BOOKED      -> target == CHECKED_IN || target == CANCELLED || target == NO_SHOW;
            case CHECKED_IN  -> target == IN_PROGRESS || target == CANCELLED || target == NO_SHOW;
            case IN_PROGRESS -> target == COMPLETED;
            case COMPLETED, CANCELLED, NO_SHOW -> false; // terminal
        };
    }
}
