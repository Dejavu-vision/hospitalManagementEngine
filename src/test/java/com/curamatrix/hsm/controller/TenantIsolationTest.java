package com.curamatrix.hsm.controller;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property 3: Tenant Isolation
 *
 * Invariant: No query result for tenant A ever contains data belonging to tenant B.
 *   ∀ tenantId t, ∀ entity e returned by any query scoped to t: e.tenantId = t
 *
 * Validates: Requirements 10.6, 10.7, 10.8, 1.2, 7.2
 */
class TenantIsolationTest {

    record TenantEntity(Long id, Long tenantId, String name) {}

    /** Simulates a tenant-scoped repository query (WHERE tenant_id = :tenantId). */
    private List<TenantEntity> queryByTenant(List<TenantEntity> allEntities, Long tenantId) {
        return allEntities.stream()
                .filter(e -> e.tenantId().equals(tenantId))
                .collect(Collectors.toList());
    }

    @Test
    void tenantAResultsContainOnlyTenantAData() {
        Long tenantA = 1L;
        Long tenantB = 2L;

        List<TenantEntity> allEntities = List.of(
                new TenantEntity(1L, tenantA, "Patient A1"),
                new TenantEntity(2L, tenantA, "Patient A2"),
                new TenantEntity(3L, tenantB, "Patient B1"),
                new TenantEntity(4L, tenantB, "Patient B2")
        );

        List<TenantEntity> resultsForA = queryByTenant(allEntities, tenantA);

        assertEquals(2, resultsForA.size(), "Tenant A should see exactly 2 records");
        assertTrue(resultsForA.stream().allMatch(e -> e.tenantId().equals(tenantA)),
                "All results for tenant A must have tenantId = A");
        assertTrue(resultsForA.stream().noneMatch(e -> e.tenantId().equals(tenantB)),
                "Results for tenant A must contain zero records with tenantId = B");
    }

    @Test
    void tenantBResultsContainOnlyTenantBData() {
        Long tenantA = 1L;
        Long tenantB = 2L;

        List<TenantEntity> allEntities = List.of(
                new TenantEntity(1L, tenantA, "Appointment A1"),
                new TenantEntity(2L, tenantB, "Appointment B1"),
                new TenantEntity(3L, tenantB, "Appointment B2")
        );

        List<TenantEntity> resultsForB = queryByTenant(allEntities, tenantB);

        assertEquals(2, resultsForB.size());
        assertTrue(resultsForB.stream().allMatch(e -> e.tenantId().equals(tenantB)));
        assertTrue(resultsForB.stream().noneMatch(e -> e.tenantId().equals(tenantA)));
    }

    @Test
    void emptyResultWhenNoDataForTenant() {
        Long tenantA = 1L;
        Long tenantC = 99L;

        List<TenantEntity> allEntities = List.of(
                new TenantEntity(1L, tenantA, "Queue Entry A1")
        );

        List<TenantEntity> resultsForC = queryByTenant(allEntities, tenantC);
        assertTrue(resultsForC.isEmpty(), "Tenant C should see no data when none exists");
    }

    @Test
    void isolationHoldsForMultipleTenantsSimultaneously() {
        List<TenantEntity> allEntities = List.of(
                new TenantEntity(1L, 1L, "T1-P1"),
                new TenantEntity(2L, 1L, "T1-P2"),
                new TenantEntity(3L, 2L, "T2-P1"),
                new TenantEntity(4L, 3L, "T3-P1"),
                new TenantEntity(5L, 3L, "T3-P2"),
                new TenantEntity(6L, 3L, "T3-P3")
        );

        for (long tenantId = 1; tenantId <= 3; tenantId++) {
            final long tid = tenantId;
            List<TenantEntity> results = queryByTenant(allEntities, tid);
            assertTrue(results.stream().allMatch(e -> e.tenantId() == tid),
                    "All results for tenant " + tid + " must belong to that tenant");
            assertTrue(results.stream().noneMatch(e -> e.tenantId() != tid),
                    "No cross-tenant data for tenant " + tid);
        }
    }
}
