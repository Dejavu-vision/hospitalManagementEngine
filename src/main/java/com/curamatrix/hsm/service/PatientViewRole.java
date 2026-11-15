package com.curamatrix.hsm.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Resolves which profile view the authenticated caller is entitled to. */
public enum PatientViewRole {
    DOCTOR, RECEPTIONIST, ADMIN, NONE;

    public static PatientViewRole current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return NONE;
        boolean admin = false, doctor = false, receptionist = false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            switch (ga.getAuthority()) {
                case "ROLE_ADMIN", "ROLE_SUPER_ADMIN" -> admin = true;
                case "ROLE_DOCTOR" -> doctor = true;
                case "ROLE_RECEPTIONIST" -> receptionist = true;
                default -> { }
            }
        }
        if (admin) return ADMIN;
        if (doctor) return DOCTOR;
        if (receptionist) return RECEPTIONIST;
        return NONE;
    }

    public boolean canSeeClinical() {
        return this == DOCTOR || this == ADMIN;
    }

    public boolean canSeeFinancial() {
        return this == RECEPTIONIST || this == ADMIN;
    }
}
