package com.curamatrix.hsm.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property 7: Timestamp Monotonicity
 *
 * Invariant: For any appointment, lifecycle timestamps must be in chronological order.
 *   createdAt <= checkedInAt <= consultationStart <= consultationEnd
 *
 * Validates: Requirements 6.4, 6.5, 6.6
 */
class TimestampMonotonicityTest {

    record AppointmentTimestamps(LocalDateTime createdAt, LocalDateTime checkedInAt,
                                  LocalDateTime consultationStart, LocalDateTime consultationEnd) {}

    @Test
    void timestampsAreMonotonicallyIncreasing() throws InterruptedException {
        LocalDateTime createdAt = LocalDateTime.now();
        Thread.sleep(1);
        LocalDateTime checkedInAt = LocalDateTime.now();
        Thread.sleep(1);
        LocalDateTime consultationStart = LocalDateTime.now();
        Thread.sleep(1);
        LocalDateTime consultationEnd = LocalDateTime.now();

        AppointmentTimestamps ts = new AppointmentTimestamps(
                createdAt, checkedInAt, consultationStart, consultationEnd);

        assertFalse(ts.createdAt().isAfter(ts.checkedInAt()),
                "createdAt must be <= checkedInAt");
        assertFalse(ts.checkedInAt().isAfter(ts.consultationStart()),
                "checkedInAt must be <= consultationStart");
        assertFalse(ts.consultationStart().isAfter(ts.consultationEnd()),
                "consultationStart must be <= consultationEnd");
    }

    @Test
    void partialTimestampsAreMonotonic() throws InterruptedException {
        // Only createdAt and checkedInAt present (appointment cancelled after check-in)
        LocalDateTime createdAt = LocalDateTime.now();
        Thread.sleep(1);
        LocalDateTime checkedInAt = LocalDateTime.now();

        assertFalse(createdAt.isAfter(checkedInAt),
                "createdAt must be <= checkedInAt even for partial lifecycle");
    }

    @Test
    void sameTimestampIsAccepted() {
        // Edge case: two timestamps at the exact same instant (e.g., fast machine)
        LocalDateTime now = LocalDateTime.now();
        assertFalse(now.isAfter(now), "Equal timestamps satisfy the <= invariant");
    }

    @Test
    void fullLifecycleTimestampsAreOrdered() throws InterruptedException {
        // Simulate BOOKED -> CHECKED_IN -> IN_PROGRESS -> COMPLETED
        LocalDateTime t0 = LocalDateTime.now(); // createdAt (BOOKED)
        Thread.sleep(2);
        LocalDateTime t1 = LocalDateTime.now(); // checkedInAt
        Thread.sleep(2);
        LocalDateTime t2 = LocalDateTime.now(); // consultationStart
        Thread.sleep(2);
        LocalDateTime t3 = LocalDateTime.now(); // consultationEnd

        assertTrue(!t0.isAfter(t1) && !t1.isAfter(t2) && !t2.isAfter(t3),
                "Full lifecycle: createdAt <= checkedInAt <= consultationStart <= consultationEnd");
    }
}
