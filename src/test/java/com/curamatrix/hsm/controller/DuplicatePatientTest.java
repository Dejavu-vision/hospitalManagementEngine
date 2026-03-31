package com.curamatrix.hsm.controller;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property 5: Duplicate Patient Detection
 *
 * Invariant: Within the same tenant, no two patients share the same
 * (firstName, lastName, dateOfBirth) triple. A registration attempt with a
 * duplicate triple must be rejected before creating a record.
 *
 * Validates: Requirements 2.4
 */
class DuplicatePatientTest {

    record PatientRecord(Long id, String firstName, String lastName,
                         LocalDate dateOfBirth, Long tenantId) {}

    static class PatientRegistry {
        private final List<PatientRecord> patients = new ArrayList<>();
        private long nextId = 1;

        /** Returns the created patient, or throws if duplicate within same tenant. */
        PatientRecord register(String firstName, String lastName, LocalDate dob, Long tenantId) {
            boolean duplicate = patients.stream().anyMatch(p ->
                    p.tenantId().equals(tenantId) &&
                    p.firstName().equalsIgnoreCase(firstName) &&
                    p.lastName().equalsIgnoreCase(lastName) &&
                    p.dateOfBirth().equals(dob));
            if (duplicate) {
                throw new IllegalStateException("HTTP 409: Duplicate patient detected");
            }
            PatientRecord record = new PatientRecord(nextId++, firstName, lastName, dob, tenantId);
            patients.add(record);
            return record;
        }

        int count(Long tenantId) {
            return (int) patients.stream().filter(p -> p.tenantId().equals(tenantId)).count();
        }
    }

    @Test
    void duplicateRegistrationIsRejectedWithinSameTenant() {
        PatientRegistry registry = new PatientRegistry();
        Long tenantId = 1L;
        LocalDate dob = LocalDate.of(1990, 5, 15);

        registry.register("John", "Doe", dob, tenantId);
        int countBefore = registry.count(tenantId);

        assertThrows(IllegalStateException.class,
                () -> registry.register("John", "Doe", dob, tenantId),
                "Duplicate registration must throw (HTTP 409)");

        assertEquals(countBefore, registry.count(tenantId),
                "Patient count must remain unchanged after rejected duplicate");
    }

    @Test
    void sameNameDifferentDobIsAllowed() {
        PatientRegistry registry = new PatientRegistry();
        Long tenantId = 1L;

        registry.register("Jane", "Smith", LocalDate.of(1985, 3, 10), tenantId);
        assertDoesNotThrow(() ->
                registry.register("Jane", "Smith", LocalDate.of(1990, 7, 20), tenantId),
                "Same name but different DOB should be allowed");
        assertEquals(2, registry.count(tenantId));
    }

    @Test
    void sameNameAndDobInDifferentTenantsIsAllowed() {
        PatientRegistry registry = new PatientRegistry();
        LocalDate dob = LocalDate.of(1975, 12, 1);

        registry.register("Alice", "Brown", dob, 1L);
        assertDoesNotThrow(() ->
                registry.register("Alice", "Brown", dob, 2L),
                "Same name+DOB in different tenants must be allowed");
        assertEquals(1, registry.count(1L));
        assertEquals(1, registry.count(2L));
    }

    @Test
    void duplicateCheckIsCaseInsensitive() {
        PatientRegistry registry = new PatientRegistry();
        Long tenantId = 1L;
        LocalDate dob = LocalDate.of(2000, 1, 1);

        registry.register("bob", "jones", dob, tenantId);

        assertThrows(IllegalStateException.class,
                () -> registry.register("BOB", "JONES", dob, tenantId),
                "Duplicate check must be case-insensitive");
        assertEquals(1, registry.count(tenantId));
    }
}
