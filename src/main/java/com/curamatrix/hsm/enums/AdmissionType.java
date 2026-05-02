package com.curamatrix.hsm.enums;

public enum AdmissionType {
    DIRECT,       // Walk-in directly to IPD (no prior OPD visit required)
    EMERGENCY,    // Emergency/casualty admission
    OPD_CONVERT,  // Converted from an existing OPD appointment
    REFERRAL      // Referred from another hospital or clinic
}
