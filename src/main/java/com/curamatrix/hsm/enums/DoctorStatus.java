package com.curamatrix.hsm.enums;

/**
 * Real-time status of a doctor during their duty day.
 * Updated by admin or receptionist as the day progresses.
 */
public enum DoctorStatus {
    ON_DUTY,          // Present, accepting patients
    IN_CONSULTATION,  // Currently with a patient (auto-set by appointment system)
    ON_BREAK,         // Stepped out briefly — back in X minutes
    IN_SURGERY,       // In OT — longer absence, estimated return time set
    OFF_DUTY          // Not available today
}
