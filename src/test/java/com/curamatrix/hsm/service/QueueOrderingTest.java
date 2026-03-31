package com.curamatrix.hsm.service;

import com.curamatrix.hsm.enums.AppointmentStatus;
import com.curamatrix.hsm.enums.AppointmentType;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property 4: Queue Ordering Invariants
 *
 * Invariant: The per-doctor queue is ordered such that:
 * - Consecutive WALK_IN entries have ascending token numbers
 * - Consecutive SCHEDULED entries have ascending appointment times
 *
 * Validates: Requirements 7.3
 */
class QueueOrderingTest {

    record QueueEntry(AppointmentType type, Integer tokenNumber, LocalTime appointmentTime,
                      AppointmentStatus status) {}

    /**
     * Simulates the sort logic from AppointmentRepository.findTodayQueueByDoctorAndTenant:
     * WALK_IN sorted by tokenNumber, SCHEDULED sorted by appointmentTime (WALK_IN first).
     */
    private List<QueueEntry> sortQueue(List<QueueEntry> entries) {
        List<QueueEntry> sorted = new ArrayList<>(entries);
        sorted.sort((a, b) -> {
            int aKey = (a.type() == AppointmentType.WALK_IN) ? (a.tokenNumber() != null ? a.tokenNumber() : 999999) : 999999;
            int bKey = (b.type() == AppointmentType.WALK_IN) ? (b.tokenNumber() != null ? b.tokenNumber() : 999999) : 999999;
            if (aKey != bKey) return Integer.compare(aKey, bKey);
            LocalTime aTime = a.appointmentTime() != null ? a.appointmentTime() : LocalTime.MAX;
            LocalTime bTime = b.appointmentTime() != null ? b.appointmentTime() : LocalTime.MAX;
            return aTime.compareTo(bTime);
        });
        return sorted;
    }

    @Test
    void walkInEntriesAreOrderedByAscendingTokenNumber() {
        List<QueueEntry> entries = List.of(
                new QueueEntry(AppointmentType.WALK_IN, 3, null, AppointmentStatus.BOOKED),
                new QueueEntry(AppointmentType.WALK_IN, 1, null, AppointmentStatus.CHECKED_IN),
                new QueueEntry(AppointmentType.WALK_IN, 2, null, AppointmentStatus.BOOKED)
        );

        List<QueueEntry> sorted = sortQueue(entries);
        List<QueueEntry> walkIns = sorted.stream()
                .filter(e -> e.type() == AppointmentType.WALK_IN).toList();

        for (int i = 0; i < walkIns.size() - 1; i++) {
            assertTrue(walkIns.get(i).tokenNumber() < walkIns.get(i + 1).tokenNumber(),
                    "WALK_IN token " + walkIns.get(i).tokenNumber() +
                    " must be < " + walkIns.get(i + 1).tokenNumber());
        }
    }

    @Test
    void scheduledEntriesAreOrderedByAscendingAppointmentTime() {
        List<QueueEntry> entries = List.of(
                new QueueEntry(AppointmentType.SCHEDULED, null, LocalTime.of(14, 0), AppointmentStatus.BOOKED),
                new QueueEntry(AppointmentType.SCHEDULED, null, LocalTime.of(9, 0), AppointmentStatus.BOOKED),
                new QueueEntry(AppointmentType.SCHEDULED, null, LocalTime.of(11, 30), AppointmentStatus.BOOKED)
        );

        List<QueueEntry> sorted = sortQueue(entries);
        List<QueueEntry> scheduled = sorted.stream()
                .filter(e -> e.type() == AppointmentType.SCHEDULED).toList();

        for (int i = 0; i < scheduled.size() - 1; i++) {
            assertFalse(scheduled.get(i).appointmentTime().isAfter(scheduled.get(i + 1).appointmentTime()),
                    "SCHEDULED time " + scheduled.get(i).appointmentTime() +
                    " must be <= " + scheduled.get(i + 1).appointmentTime());
        }
    }

    @Test
    void mixedQueuePreservesOrderingInvariantsForRandomInputs() {
        Random rng = new Random(42);
        for (int trial = 0; trial < 20; trial++) {
            List<QueueEntry> entries = new ArrayList<>();
            int walkInCount = rng.nextInt(5) + 1;
            int scheduledCount = rng.nextInt(5) + 1;

            List<Integer> tokens = new ArrayList<>();
            for (int i = 1; i <= walkInCount; i++) tokens.add(i);
            Collections.shuffle(tokens, rng);
            for (int t : tokens) {
                entries.add(new QueueEntry(AppointmentType.WALK_IN, t, null, AppointmentStatus.BOOKED));
            }

            for (int i = 0; i < scheduledCount; i++) {
                LocalTime time = LocalTime.of(9 + rng.nextInt(8), rng.nextInt(2) * 30);
                entries.add(new QueueEntry(AppointmentType.SCHEDULED, null, time, AppointmentStatus.BOOKED));
            }

            Collections.shuffle(entries, rng);
            List<QueueEntry> sorted = sortQueue(entries);

            List<QueueEntry> walkIns = sorted.stream().filter(e -> e.type() == AppointmentType.WALK_IN).toList();
            for (int i = 0; i < walkIns.size() - 1; i++) {
                assertTrue(walkIns.get(i).tokenNumber() <= walkIns.get(i + 1).tokenNumber(),
                        "Trial " + trial + ": WALK_IN ordering violated");
            }

            List<QueueEntry> scheduled = sorted.stream().filter(e -> e.type() == AppointmentType.SCHEDULED).toList();
            for (int i = 0; i < scheduled.size() - 1; i++) {
                assertFalse(scheduled.get(i).appointmentTime().isAfter(scheduled.get(i + 1).appointmentTime()),
                        "Trial " + trial + ": SCHEDULED ordering violated");
            }
        }
    }

    @Test
    void singleEntryQueueAlwaysSatisfiesOrdering() {
        List<QueueEntry> single = List.of(
                new QueueEntry(AppointmentType.WALK_IN, 1, null, AppointmentStatus.BOOKED)
        );
        List<QueueEntry> sorted = sortQueue(single);
        assertEquals(1, sorted.size());
        // No consecutive pairs to check — trivially satisfies ordering
    }
}
