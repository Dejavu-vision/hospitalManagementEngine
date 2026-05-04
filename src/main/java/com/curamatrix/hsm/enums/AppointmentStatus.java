package com.curamatrix.hsm.enums;

public enum AppointmentStatus {
    BOOKED, CHECKED_IN, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW;

    public boolean canTransitionTo(AppointmentStatus target) {
        if (this == target) return false;
        return switch (this) {
            // Receptionist can call a BOOKED patient directly (skip check-in step)
            case BOOKED      -> target == CHECKED_IN || target == IN_PROGRESS
                                || target == CANCELLED || target == NO_SHOW;
            // Checked-in patient can be called, cancelled, or marked no-show
            case CHECKED_IN  -> target == IN_PROGRESS || target == CANCELLED || target == NO_SHOW;
            // In-progress: done, skip back to waiting, no-show, or recall (re-call same patient)
            case IN_PROGRESS -> target == COMPLETED || target == CHECKED_IN || target == NO_SHOW;
            case COMPLETED, CANCELLED, NO_SHOW -> false; // terminal
        };
    }
}
