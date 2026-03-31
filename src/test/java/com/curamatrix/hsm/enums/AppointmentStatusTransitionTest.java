package com.curamatrix.hsm.enums;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.curamatrix.hsm.enums.AppointmentStatus.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based style tests for AppointmentStatus transition invariants.
 *
 * Validates: Requirements 6.1, 6.2
 *
 * Property 1: Status Transition Invariants
 *   1a. No self-transitions
 *   1b. Terminal states have no outgoing edges
 *   1c. No reverse transitions
 *   1d. Only the 7 defined edges return true
 */
class AppointmentStatusTransitionTest {

    /** Property 1a — No self-transitions: s.canTransitionTo(s) must be false for all s. */
    @Test
    void noSelfTransitions() {
        for (AppointmentStatus s : AppointmentStatus.values()) {
            assertFalse(s.canTransitionTo(s),
                    "Self-transition should not be allowed for " + s);
        }
    }

    /** Property 1b — Terminal states (COMPLETED, CANCELLED, NO_SHOW) have no outgoing edges. */
    @Test
    void terminalStatesHaveNoOutgoingEdges() {
        List<AppointmentStatus> terminals = List.of(COMPLETED, CANCELLED, NO_SHOW);
        for (AppointmentStatus terminal : terminals) {
            for (AppointmentStatus target : AppointmentStatus.values()) {
                assertFalse(terminal.canTransitionTo(target),
                        "Terminal state " + terminal + " should not transition to " + target);
            }
        }
    }

    /**
     * Property 1c — No reverse transitions:
     * if s.canTransitionTo(t) is true, then t.canTransitionTo(s) must be false.
     */
    @Test
    void noReverseTransitions() {
        for (AppointmentStatus s : AppointmentStatus.values()) {
            for (AppointmentStatus t : AppointmentStatus.values()) {
                if (s.canTransitionTo(t)) {
                    assertFalse(t.canTransitionTo(s),
                            "Reverse transition should not be allowed: " + t + " -> " + s
                            + " (forward " + s + " -> " + t + " is valid)");
                }
            }
        }
    }

    /**
     * Property 1d — Only the 7 defined edges return true; every other (source, target) pair
     * must return false.
     *
     * Defined edges:
     *   BOOKED->CHECKED_IN, BOOKED->CANCELLED, BOOKED->NO_SHOW,
     *   CHECKED_IN->IN_PROGRESS, CHECKED_IN->CANCELLED, CHECKED_IN->NO_SHOW,
     *   IN_PROGRESS->COMPLETED
     */
    @Test
    void onlyDefinedEdgesReturnTrue() {
        Set<String> allowed = Set.of(
                "BOOKED->CHECKED_IN",
                "BOOKED->CANCELLED",
                "BOOKED->NO_SHOW",
                "CHECKED_IN->IN_PROGRESS",
                "CHECKED_IN->CANCELLED",
                "CHECKED_IN->NO_SHOW",
                "IN_PROGRESS->COMPLETED"
        );

        for (AppointmentStatus s : AppointmentStatus.values()) {
            for (AppointmentStatus t : AppointmentStatus.values()) {
                String edge = s + "->" + t;
                assertEquals(
                        allowed.contains(edge),
                        s.canTransitionTo(t),
                        "Unexpected result for edge: " + edge
                );
            }
        }
    }
}
