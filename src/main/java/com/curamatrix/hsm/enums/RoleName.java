package com.curamatrix.hsm.enums;

public enum RoleName {
    ROLE_SUPER_ADMIN,  // Platform-level admin (manages all tenants)
    ROLE_ADMIN,        // Tenant-level admin (manages single hospital)
    ROLE_DOCTOR,
    ROLE_RECEPTIONIST
}
