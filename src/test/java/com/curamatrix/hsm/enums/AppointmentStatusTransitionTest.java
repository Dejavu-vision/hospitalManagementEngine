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
 *   1b. Terminal states (COMPLETED, CANCELLED) have no outgoing edges
 *   1c. Only the defined edges in the state machine return true
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

    /** Property 1b — Terminal states (CANCELLED) have no outgoing edges. */
    @Test
    void terminalStatesHaveNoOutgoingEdges() {
        List<AppointmentStatus> terminals = List.of(CANCELLED);
        for (AppointmentStatus terminal : terminals) {
            for (AppointmentStatus target : AppointmentStatus.values()) {
                assertFalse(terminal.canTransitionTo(target),
                        "Terminal state " + terminal + " should not transition to " + target);
            }
        }
    }

    /**
     * Property 1c — Only the defined edges return true; every other (source, target) pair
     * must return false.
     */
    @Test
    void onlyDefinedEdgesReturnTrue() {
        Set<String> allowed = Set.of(
                // BOOKED ->
                "BOOKED->CHECKED_IN",
                "BOOKED->IN_PROGRESS",
                "BOOKED->CANCELLED",
                "BOOKED->NO_SHOW",

                // CHECKED_IN ->
                "CHECKED_IN->IN_PROGRESS",
                "CHECKED_IN->CANCELLED",
                "CHECKED_IN->NO_SHOW",
                "CHECKED_IN->BOOKED",

                // IN_PROGRESS ->
                "IN_PROGRESS->COMPLETED",
                "IN_PROGRESS->CHECKED_IN",
                "IN_PROGRESS->BOOKED",
                "IN_PROGRESS->NO_SHOW",
                "IN_PROGRESS->RECALLED",
                "IN_PROGRESS->ON_HOLD",

                // ON_HOLD ->
                "ON_HOLD->IN_PROGRESS",
                "ON_HOLD->NO_SHOW",
                "ON_HOLD->BOOKED",

                // RECALLED ->
                "RECALLED->IN_PROGRESS",
                "RECALLED->COMPLETED",
                "RECALLED->CHECKED_IN",
                "RECALLED->NO_SHOW",

                // COMPLETED ->
                "COMPLETED->CHECKED_IN",
                "COMPLETED->BOOKED",

                // NO_SHOW ->
                "NO_SHOW->BOOKED"
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
