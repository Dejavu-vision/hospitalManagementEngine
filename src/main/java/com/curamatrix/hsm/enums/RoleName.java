package com.curamatrix.hsm.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum RoleName {
    ROLE_SUPER_ADMIN,  // Platform-level admin (manages all tenants)
    ROLE_ADMIN,        // Tenant-level admin (manages single hospital)
    ROLE_DOCTOR,
    ROLE_RECEPTIONIST,
    ROLE_LAB_STAFF,    // Laboratory personnel (canonical in DB & security)
    ROLE_LAB_TECH,     // Alias for Lab Technician
    ROLE_NURSE,
    ROLE_PHARMACIST;

    @JsonCreator
    public static RoleName fromString(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase();
        if ("ROLE_LAB_TECH".equals(normalized) || "LAB_TECH".equals(normalized)
                || "ROLE_LAB_STAFF".equals(normalized) || "LAB_STAFF".equals(normalized)
                || "LAB".equals(normalized) || "LAB_TECHNICIAN".equals(normalized)) {
            return ROLE_LAB_STAFF;
        }
        if ("ROLE_NURSE".equals(normalized) || "NURSE".equals(normalized)) {
            return ROLE_NURSE;
        }
        if ("ROLE_PHARMACIST".equals(normalized) || "PHARMACIST".equals(normalized)) {
            return ROLE_PHARMACIST;
        }
        for (RoleName r : values()) {
            if (r.name().equalsIgnoreCase(normalized) || r.name().replace("ROLE_", "").equalsIgnoreCase(normalized)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }
}
