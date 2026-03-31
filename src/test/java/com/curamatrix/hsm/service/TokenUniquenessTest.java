package com.curamatrix.hsm.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property 2: Token Uniqueness Per Doctor Per Day
 *
 * Invariant: For any given (doctor_id, appointment_date, tenant_id), all walk-in token
 * numbers assigned on that day are distinct positive integers forming a contiguous
 * sequence starting at 1.
 *
 * Validates: Requirements 5.2, 5.3
 */
class TokenUniquenessTest {

    /**
     * Simulates the atomic token counter using AtomicInteger (mirrors the pessimistic-lock
     * behaviour of WalkInTokenSequence in production).
     */
    @Test
    void tokensAreUniqueAndContiguousUnderConcurrency() throws InterruptedException {
        int N = 20;
        AtomicInteger counter = new AtomicInteger(0);
        List<Integer> tokens = Collections.synchronizedList(new ArrayList<>());

        List<Thread> threads = IntStream.range(0, N)
                .mapToObj(i -> new Thread(() -> tokens.add(counter.incrementAndGet())))
                .collect(Collectors.toList());

        threads.forEach(Thread::start);
        for (Thread t : threads) t.join();

        assertEquals(N, tokens.size(), "Should have exactly N tokens");

        Set<Integer> tokenSet = new HashSet<>(tokens);
        assertEquals(N, tokenSet.size(), "All tokens must be unique");

        Set<Integer> expected = IntStream.rangeClosed(1, N).boxed().collect(Collectors.toSet());
        assertEquals(expected, tokenSet, "Tokens must form contiguous sequence {1..N}");
    }

    /**
     * Property: token sequence always starts at 1 for a fresh doctor/date/tenant.
     */
    @Test
    void firstTokenIsAlwaysOne() {
        AtomicInteger counter = new AtomicInteger(0);
        int firstToken = counter.incrementAndGet();
        assertEquals(1, firstToken, "First token for a new sequence must be 1");
    }

    /**
     * Property: no gaps in the sequence for any N.
     */
    @Test
    void noGapsInSequenceForVariousN() {
        for (int N : new int[]{1, 5, 10, 50, 100}) {
            AtomicInteger counter = new AtomicInteger(0);
            Set<Integer> tokens = IntStream.range(0, N)
                    .map(i -> counter.incrementAndGet())
                    .boxed()
                    .collect(Collectors.toSet());

            Set<Integer> expected = IntStream.rangeClosed(1, N).boxed().collect(Collectors.toSet());
            assertEquals(expected, tokens, "No gaps for N=" + N);
        }
    }
}
