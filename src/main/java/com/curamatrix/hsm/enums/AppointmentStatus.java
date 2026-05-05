package com.curamatrix.hsm.enums;

public enum AppointmentStatus {
    BOOKED, CHECKED_IN, IN_PROGRESS, RECALLED, COMPLETED, CANCELLED, NO_SHOW;

    public boolean canTransitionTo(AppointmentStatus target) {
        if (this == target) return false;
        return switch (this) {
            // Receptionist can call a BOOKED patient directly (skip check-in step)
            // or cancel/no-show before they arrive
            case BOOKED      -> target == CHECKED_IN || target == IN_PROGRESS
                                || target == CANCELLED || target == NO_SHOW;
            // Checked-in patient can be called, cancelled, or marked no-show
            case CHECKED_IN  -> target == IN_PROGRESS || target == CANCELLED
                                || target == NO_SHOW || target == BOOKED;
            // In-progress: done, skip back to waiting (CHECKED_IN or BOOKED), no-show, or recall
            case IN_PROGRESS -> target == COMPLETED || target == CHECKED_IN
                                || target == BOOKED || target == NO_SHOW || target == RECALLED;
            // Recalled: can be called back in, completed, skipped back, or marked no-show
            case RECALLED    -> target == IN_PROGRESS || target == COMPLETED
                                || target == CHECKED_IN || target == NO_SHOW;
            case COMPLETED, CANCELLED -> false; // terminal
            case NO_SHOW     -> target == BOOKED; // restorable
        };
    }
}
