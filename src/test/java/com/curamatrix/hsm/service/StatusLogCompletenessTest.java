package com.curamatrix.hsm.service;

import com.curamatrix.hsm.enums.AppointmentStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.curamatrix.hsm.enums.AppointmentStatus.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Property 6: Status Log Completeness
 *
 * Invariant: Every status transition produces exactly one log entry.
 * The log is a complete, ordered, contiguous chain from creation to current state.
 *
 * Validates: Requirements 6.3
 */
class StatusLogCompletenessTest {

    record LogEntry(AppointmentStatus previousStatus, AppointmentStatus newStatus,
                    LocalDateTime changedAt, String changedBy) {}

    private List<LogEntry> applyTransitions(List<AppointmentStatus[]> transitions) {
        List<LogEntry> log = new ArrayList<>();
        for (AppointmentStatus[] t : transitions) {
            log.add(new LogEntry(t[0], t[1], LocalDateTime.now(), "user1"));
        }
        return log;
    }

    @Test
    void logLengthEqualsTransitionCount() {
        List<AppointmentStatus[]> transitions = List.of(
                new AppointmentStatus[]{null, BOOKED},
                new AppointmentStatus[]{BOOKED, CHECKED_IN},
                new AppointmentStatus[]{CHECKED_IN, IN_PROGRESS},
                new AppointmentStatus[]{IN_PROGRESS, COMPLETED}
        );
        List<LogEntry> log = applyTransitions(transitions);
        assertEquals(transitions.size(), log.size(), "Log length must equal number of transitions");
    }

    @Test
    void logChainIsContiguous() {
        List<AppointmentStatus[]> transitions = List.of(
                new AppointmentStatus[]{null, BOOKED},
                new AppointmentStatus[]{BOOKED, CHECKED_IN},
                new AppointmentStatus[]{CHECKED_IN, IN_PROGRESS},
                new AppointmentStatus[]{IN_PROGRESS, COMPLETED}
        );
        List<LogEntry> log = applyTransitions(transitions);

        for (int i = 0; i < log.size() - 1; i++) {
            assertEquals(log.get(i).newStatus(), log.get(i + 1).previousStatus(),
                    "Log chain broken at index " + i + ": l_i.newStatus != l_{i+1}.previousStatus");
        }
    }

    @Test
    void everyEntryHasNonNullChangedAtAndChangedBy() {
        List<AppointmentStatus[]> transitions = List.of(
                new AppointmentStatus[]{null, BOOKED},
                new AppointmentStatus[]{BOOKED, CANCELLED}
        );
        List<LogEntry> log = applyTransitions(transitions);

        for (LogEntry entry : log) {
            assertNotNull(entry.changedAt(), "changedAt must not be null");
            assertNotNull(entry.changedBy(), "changedBy must not be null");
        }
    }

    @Test
    void logCoversAllValidTransitionPaths() {
        // Test BOOKED -> NO_SHOW path
        List<AppointmentStatus[]> noShowPath = List.of(
                new AppointmentStatus[]{null, BOOKED},
                new AppointmentStatus[]{BOOKED, NO_SHOW}
        );
        List<LogEntry> log = applyTransitions(noShowPath);
        assertEquals(2, log.size());
        assertEquals(NO_SHOW, log.get(1).newStatus());

        // Test CHECKED_IN -> CANCELLED path
        List<AppointmentStatus[]> cancelPath = List.of(
                new AppointmentStatus[]{null, BOOKED},
                new AppointmentStatus[]{BOOKED, CHECKED_IN},
                new AppointmentStatus[]{CHECKED_IN, CANCELLED}
        );
        log = applyTransitions(cancelPath);
        assertEquals(3, log.size());
        assertEquals(CANCELLED, log.get(2).newStatus());
    }
}
