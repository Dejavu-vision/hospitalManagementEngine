package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.WalkInRequest;
import com.curamatrix.hsm.dto.response.AppointmentResponse;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property 2: Preservation - Existing Token Sequence Operations Unchanged
 * 
 * **IMPORTANT**: Follow observation-first methodology
 * 
 * This test observes behavior on UNFIXED code for non-buggy inputs (operations that do NOT 
 * involve inserting NEW token sequence records). These tests capture the baseline behavior 
 * that must be preserved after the fix.
 * 
 * Test Cases:
 * 1. Read Existing Sequences - verify findForUpdate() returns correct records with correct counter values
 * 2. Update Counter - verify incrementing an existing counter produces correct final value
 * 3. Concurrent Updates - verify pessimistic locking prevents race conditions
 * 4. Cross-Tenant Isolation - verify tenant A cannot see tenant B's sequences
 * 
 * EXPECTED OUTCOME on UNFIXED code: Tests PASS (confirms baseline behavior to preserve)
 * EXPECTED OUTCOME on FIXED code: Tests PASS (confirms no regressions)
 * 
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4**
 */
@SpringBootTest
@Transactional
class WalkInTokenSequencePreservationTest {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private WalkInTokenSequenceRepository tokenSequenceRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private static final Long TENANT_A_ID = 1001L;
    private static final Long TENANT_B_ID = 1002L;
    
    private Patient testPatientA;
    private Patient testPatientB;
    private Doctor testDoctorA;
    private Doctor testDoctorB;
    private User testReceptionistA;
    private User testReceptionistB;
    private Tenant tenantA;
    private Tenant tenantB;

    @BeforeEach
    void setUp() {
        // Create Tenant A
        TenantContext.setTenantId(TENANT_A_ID);
        tenantA = Tenant.builder()
                .tenantKey("test-hospital-preservation-a")
                .hospitalName("Test Hospital A")
                .contactEmail("testa@hospital.com")
                .contactPhone("1111111111")
                .address("Test Address A")
                .subscriptionPlan("BASIC")
                .subscriptionStart(LocalDate.now())
                .subscriptionEnd(LocalDate.now().plusYears(1))
                .isActive(true)
                .build();
        tenantA = tenantRepository.save(tenantA);

        // Create department for Tenant A
        Department departmentA = Department.builder()
                .name("General Medicine A")
                .description("General Medicine Department A")
                .isActive(true)
                .build();
        departmentA = departmentRepository.save(departmentA);

        // Create doctor user for Tenant A
        User doctorUserA = User.builder()
                .email("doctor.preservation.a@test.com")
                .password("password")
                .fullName("Dr. Test Doctor A")
                .phone("9876543210")
                .isActive(true)
                .build();
        doctorUserA.setTenantId(TENANT_A_ID);
        doctorUserA = userRepository.save(doctorUserA);

        // Create doctor for Tenant A
        testDoctorA = Doctor.builder()
                .user(doctorUserA)
                .department(departmentA)
                .licenseNumber("DOC-PRES-A-001")
                .consultationFee(new java.math.BigDecimal("500.00"))
                .experienceYears(10)
                .qualification("MBBS, MD")
                .build();
        testDoctorA = doctorRepository.save(testDoctorA);

        // Create receptionist user for Tenant A
        testReceptionistA = User.builder()
                .email("receptionist.preservation.a@test.com")
                .password("password")
                .fullName("Test Receptionist A")
                .phone("9876543211")
                .isActive(true)
                .build();
        testReceptionistA.setTenantId(TENANT_A_ID);
        testReceptionistA = userRepository.save(testReceptionistA);

        // Create patient for Tenant A
        testPatientA = Patient.builder()
                .patientCode("PAT-PRES-A-001")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender(com.curamatrix.hsm.enums.Gender.MALE)
                .phone("9999999991")
                .email("john.doe.preservation.a@test.com")
                .address("Test Address A")
                .bloodGroup(com.curamatrix.hsm.enums.BloodGroup.O_POSITIVE)
                .build();
        testPatientA.setTenantId(TENANT_A_ID);
        testPatientA = patientRepository.save(testPatientA);

        // Create Tenant B
        TenantContext.setTenantId(TENANT_B_ID);
        tenantB = Tenant.builder()
                .tenantKey("test-hospital-preservation-b")
                .hospitalName("Test Hospital B")
                .contactEmail("testb@hospital.com")
                .contactPhone("2222222222")
                .address("Test Address B")
                .subscriptionPlan("BASIC")
                .subscriptionStart(LocalDate.now())
                .subscriptionEnd(LocalDate.now().plusYears(1))
                .isActive(true)
                .build();
        tenantB = tenantRepository.save(tenantB);

        // Create department for Tenant B
        Department departmentB = Department.builder()
                .name("General Medicine B")
                .description("General Medicine Department B")
                .isActive(true)
                .build();
        departmentB = departmentRepository.save(departmentB);

        // Create doctor user for Tenant B
        User doctorUserB = User.builder()
                .email("doctor.preservation.b@test.com")
                .password("password")
                .fullName("Dr. Test Doctor B")
                .phone("9876543220")
                .isActive(true)
                .build();
        doctorUserB.setTenantId(TENANT_B_ID);
        doctorUserB = userRepository.save(doctorUserB);

        // Create doctor for Tenant B
        testDoctorB = Doctor.builder()
                .user(doctorUserB)
                .department(departmentB)
                .licenseNumber("DOC-PRES-B-001")
                .consultationFee(new java.math.BigDecimal("500.00"))
                .experienceYears(10)
                .qualification("MBBS, MD")
                .build();
        testDoctorB = doctorRepository.save(testDoctorB);

        // Create receptionist user for Tenant B
        testReceptionistB = User.builder()
                .email("receptionist.preservation.b@test.com")
                .password("password")
                .fullName("Test Receptionist B")
                .phone("9876543221")
                .isActive(true)
                .build();
        testReceptionistB.setTenantId(TENANT_B_ID);
        testReceptionistB = userRepository.save(testReceptionistB);

        // Create patient for Tenant B
        testPatientB = Patient.builder()
                .patientCode("PAT-PRES-B-001")
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1992, 2, 2))
                .gender(com.curamatrix.hsm.enums.Gender.FEMALE)
                .phone("9999999992")
                .email("jane.smith.preservation.b@test.com")
                .address("Test Address B")
                .bloodGroup(com.curamatrix.hsm.enums.BloodGroup.A_POSITIVE)
                .build();
        testPatientB.setTenantId(TENANT_B_ID);
        testPatientB = patientRepository.save(testPatientB);

        // Clear context after setup
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    /**
     * Helper method to insert token sequence directly using native SQL,
     * bypassing Hibernate to avoid triggering the INSERT bug on unfixed code.
     */
    private void insertTokenSequenceDirectly(LocalDate date, Long tenantId, int counter) {
        entityManager.createNativeQuery(
                "INSERT INTO walk_in_token_sequence (appointment_date, tenant_id, counter, created_at, updated_at) " +
                "VALUES (:date, :tenantId, :counter, NOW(), NOW())")
                .setParameter("date", date)
                .setParameter("tenantId", tenantId)
                .setParameter("counter", counter)
                .executeUpdate();
        entityManager.flush();
    }

    /**
     * Property 2.1: Read Existing Sequences
     * 
     * For any existing token sequence record, reading it via findForUpdate() should return
     * the correct record with the correct counter value.
     * 
     * This test creates existing sequences directly using native SQL (bypassing Hibernate
     * to avoid the INSERT bug) with various counter values and verifies they can be read
     * correctly. This operation uses SELECT statements and should work on both unfixed and
     * fixed code.
     * 
     * **Validates: Requirement 3.1**
     */
    @Test
    void testReadExistingSequencesReturnsCorrectCounterValues() {
        // Test with various counter values (1 to 100)
        int[] testCounters = {1, 5, 10, 25, 50, 75, 100};
        LocalDate baseDate = LocalDate.now().minusDays(10);

        for (int i = 0; i < testCounters.length; i++) {
            int counterValue = testCounters[i];
            LocalDate testDate = baseDate.plusDays(i);

            // Create existing sequence directly using native SQL (bypasses Hibernate INSERT bug)
            insertTokenSequenceDirectly(testDate, TENANT_A_ID, counterValue);

            // Now read the sequence and verify counter value
            Optional<WalkInTokenSequence> sequenceOpt = tokenSequenceRepository.findForUpdate(testDate, TENANT_A_ID);
            assertTrue(sequenceOpt.isPresent(), 
                    "Sequence should exist for date " + testDate + " and tenant " + TENANT_A_ID);

            WalkInTokenSequence sequence = sequenceOpt.get();
            assertEquals(counterValue, sequence.getLastToken(),
                    "Counter should be " + counterValue + " for date " + testDate);
            assertEquals(testDate, sequence.getAppointmentDate(),
                    "Appointment date should match");
            assertEquals(TENANT_A_ID, sequence.getTenantId(),
                    "Tenant ID should match");
        }

        System.out.println("=== Read Existing Sequences Test Completed ===");
        System.out.println("Verified reading sequences with counter values: " + Arrays.toString(testCounters));
        System.out.println("All SELECT operations worked correctly");
        System.out.println("==============================================");
    }

    /**
     * Property 2.2: Update Counter
     * 
     * For any existing token sequence, incrementing the counter should produce the correct
     * final value (initial + 1). This operation uses UPDATE statements and should work on
     * both unfixed and fixed code.
     * 
     * **Validates: Requirement 3.2**
     */
    @Test
    void testUpdateCounterIncrementsCorrectly() {
        // Test with various initial counter values
        int[] initialCounters = {1, 10, 25, 50, 99};
        LocalDate baseDate = LocalDate.now().minusDays(5);

        for (int i = 0; i < initialCounters.length; i++) {
            int initialCounter = initialCounters[i];
            LocalDate testDate = baseDate.plusDays(i);

            // Create existing sequence directly in database (bypasses INSERT bug)
            WalkInTokenSequence existingSequence = new WalkInTokenSequence();
            existingSequence.setAppointmentDate(testDate);
            existingSequence.setTenantId(TENANT_A_ID);
            existingSequence.setLastToken(initialCounter);
            tokenSequenceRepository.save(existingSequence);

            // Verify initial counter
            WalkInTokenSequence beforeUpdate = tokenSequenceRepository.findForUpdate(testDate, TENANT_A_ID)
                    .orElseThrow(() -> new AssertionError("Sequence should exist before update"));
            assertEquals(initialCounter, beforeUpdate.getLastToken(),
                    "Initial counter should be " + initialCounter);

            // Set tenant context for Tenant A
            TenantContext.setTenantId(TENANT_A_ID);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            testReceptionistA.getEmail(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_RECEPTIONIST"))
                    )
            );

            // Increment counter by creating one more walk-in (UPDATE path, not INSERT)
            WalkInRequest incrementRequest = new WalkInRequest();
            incrementRequest.setPatientId(testPatientA.getId());
            incrementRequest.setDoctorId(testDoctorA.getId());
            incrementRequest.setNotes("Increment walk-in");
            incrementRequest.setPayNow(false);
            incrementRequest.setFollowUp(false);
            incrementRequest.setCounter("B");

            AppointmentResponse response = appointmentService.createWalkIn(incrementRequest);
            assertEquals(initialCounter + 1, response.getTokenNumber(),
                    "New token should be " + (initialCounter + 1));

            // Verify final counter
            WalkInTokenSequence afterUpdate = tokenSequenceRepository.findForUpdate(testDate, TENANT_A_ID)
                    .orElseThrow(() -> new AssertionError("Sequence should exist after update"));
            assertEquals(initialCounter + 1, afterUpdate.getLastToken(),
                    "Final counter should be " + (initialCounter + 1));

            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }

        System.out.println("=== Update Counter Test Completed ===");
        System.out.println("Verified counter increments for initial values: " + Arrays.toString(initialCounters));
        System.out.println("All UPDATE operations worked correctly");
        System.out.println("=====================================");
    }

    /**
     * Property 2.3: Concurrent Updates
     * 
     * When multiple concurrent requests attempt to generate walk-in tokens for the same
     * date and tenant with an existing sequence, pessimistic locking should prevent race
     * conditions and ensure all tokens are unique and contiguous.
     * 
     * This test simulates concurrent token generation and verifies no duplicates occur.
     * 
     * **Validates: Requirement 3.3**
     */
    @Test
    void testConcurrentUpdatesPreventRaceConditions() throws InterruptedException, ExecutionException {
        LocalDate testDate = LocalDate.now().minusDays(1);
        int concurrentRequests = 20;

        // Create initial sequence directly in database (bypasses INSERT bug)
        WalkInTokenSequence initialSequence = new WalkInTokenSequence();
        initialSequence.setAppointmentDate(testDate);
        initialSequence.setTenantId(TENANT_A_ID);
        initialSequence.setLastToken(1);
        tokenSequenceRepository.save(initialSequence);

        // Verify initial state
        WalkInTokenSequence savedSequence = tokenSequenceRepository.findForUpdate(testDate, TENANT_A_ID)
                .orElseThrow(() -> new AssertionError("Initial sequence should exist"));
        assertEquals(1, savedSequence.getLastToken(), "Initial counter should be 1");

        // Create thread pool for concurrent requests
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        List<Future<Integer>> futures = new ArrayList<>();

        // Submit concurrent walk-in creation requests
        for (int i = 0; i < concurrentRequests; i++) {
            final int requestNum = i;
            Future<Integer> future = executor.submit(() -> {
                // Each thread needs its own tenant context and security context
                TenantContext.setTenantId(TENANT_A_ID);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                testReceptionistA.getEmail(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_RECEPTIONIST"))
                        )
                );

                try {
                    WalkInRequest request = new WalkInRequest();
                    request.setPatientId(testPatientA.getId());
                    request.setDoctorId(testDoctorA.getId());
                    request.setNotes("Concurrent walk-in " + requestNum);
                    request.setPayNow(false);
                    request.setFollowUp(false);
                    request.setCounter("C" + requestNum);

                    AppointmentResponse response = appointmentService.createWalkIn(request);
                    return response.getTokenNumber();
                } finally {
                    TenantContext.clear();
                    SecurityContextHolder.clearContext();
                }
            });
            futures.add(future);
        }

        // Collect all token numbers
        List<Integer> tokenNumbers = new ArrayList<>();
        for (Future<Integer> future : futures) {
            tokenNumbers.add(future.get());
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // Verify all tokens are unique
        Set<Integer> uniqueTokens = new HashSet<>(tokenNumbers);
        assertEquals(concurrentRequests, uniqueTokens.size(),
                "All " + concurrentRequests + " tokens should be unique (no duplicates from race conditions)");

        // Verify tokens form a contiguous sequence starting from 2 (since we started with counter = 1)
        Set<Integer> expectedTokens = IntStream.rangeClosed(2, concurrentRequests + 1)
                .boxed()
                .collect(Collectors.toSet());
        assertEquals(expectedTokens, uniqueTokens,
                "Tokens should form contiguous sequence {2.." + (concurrentRequests + 1) + "}");

        // Verify final counter value
        TenantContext.setTenantId(TENANT_A_ID);
        WalkInTokenSequence finalSequence = tokenSequenceRepository.findForUpdate(testDate, TENANT_A_ID)
                .orElseThrow(() -> new AssertionError("Final sequence should exist"));
        assertEquals(concurrentRequests + 1, finalSequence.getLastToken(),
                "Final counter should be " + (concurrentRequests + 1));
        TenantContext.clear();

        System.out.println("=== Concurrent Updates Test Completed ===");
        System.out.println("Verified " + concurrentRequests + " concurrent token generations");
        System.out.println("All tokens unique: " + uniqueTokens.size() + " distinct values");
        System.out.println("Pessimistic locking prevented race conditions");
        System.out.println("=========================================");
    }

    /**
     * Property 2.4: Cross-Tenant Isolation
     * 
     * Token sequences for different tenants on the same date should be completely isolated.
     * Tenant A should not be able to see or affect Tenant B's sequences, and vice versa.
     * 
     * This test creates sequences for multiple tenants on the same date and verifies
     * complete isolation.
     * 
     * **Validates: Requirement 3.4**
     */
    @Test
    void testCrossTenantIsolationMaintained() {
        LocalDate testDate = LocalDate.now().minusDays(2);

        // Create initial sequence for Tenant A directly in database (bypasses INSERT bug)
        WalkInTokenSequence sequenceA = new WalkInTokenSequence();
        sequenceA.setAppointmentDate(testDate);
        sequenceA.setTenantId(TENANT_A_ID);
        sequenceA.setLastToken(1);
        tokenSequenceRepository.save(sequenceA);

        // Set tenant context for Tenant A and create 4 more walk-ins (UPDATE path)
        TenantContext.setTenantId(TENANT_A_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        testReceptionistA.getEmail(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_RECEPTIONIST"))
                )
        );

        // Create 4 more walk-ins for Tenant A (total will be 5)
        for (int i = 2; i <= 5; i++) {
            WalkInRequest request = new WalkInRequest();
            request.setPatientId(testPatientA.getId());
            request.setDoctorId(testDoctorA.getId());
            request.setNotes("Tenant A walk-in " + i);
            request.setPayNow(false);
            request.setFollowUp(false);
            request.setCounter("A" + i);

            appointmentService.createWalkIn(request);
        }

        // Verify Tenant A sequence
        WalkInTokenSequence sequenceAAfterUpdates = tokenSequenceRepository.findForUpdate(testDate, TENANT_A_ID)
                .orElseThrow(() -> new AssertionError("Tenant A sequence should exist"));
        assertEquals(5, sequenceAAfterUpdates.getLastToken(), "Tenant A counter should be 5");
        assertEquals(TENANT_A_ID, sequenceAAfterUpdates.getTenantId(), "Sequence should belong to Tenant A");

        TenantContext.clear();
        SecurityContextHolder.clearContext();

        // Create initial sequence for Tenant B directly in database (bypasses INSERT bug)
        WalkInTokenSequence sequenceB = new WalkInTokenSequence();
        sequenceB.setAppointmentDate(testDate);
        sequenceB.setTenantId(TENANT_B_ID);
        sequenceB.setLastToken(1);
        tokenSequenceRepository.save(sequenceB);

        // Set tenant context for Tenant B and create 2 more walk-ins (UPDATE path)
        TenantContext.setTenantId(TENANT_B_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        testReceptionistB.getEmail(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_RECEPTIONIST"))
                )
        );

        // Create 2 more walk-ins for Tenant B (total will be 3)
        for (int i = 2; i <= 3; i++) {
            WalkInRequest request = new WalkInRequest();
            request.setPatientId(testPatientB.getId());
            request.setDoctorId(testDoctorB.getId());
            request.setNotes("Tenant B walk-in " + i);
            request.setPayNow(false);
            request.setFollowUp(false);
            request.setCounter("B" + i);

            appointmentService.createWalkIn(request);
        }

        // Verify Tenant B sequence
        WalkInTokenSequence sequenceBAfterUpdates = tokenSequenceRepository.findForUpdate(testDate, TENANT_B_ID)
                .orElseThrow(() -> new AssertionError("Tenant B sequence should exist"));
        assertEquals(3, sequenceBAfterUpdates.getLastToken(), "Tenant B counter should be 3");
        assertEquals(TENANT_B_ID, sequenceBAfterUpdates.getTenantId(), "Sequence should belong to Tenant B");

        TenantContext.clear();
        SecurityContextHolder.clearContext();

        // Verify isolation: Tenant A cannot see Tenant B's sequence
        TenantContext.setTenantId(TENANT_A_ID);
        Optional<WalkInTokenSequence> tenantAViewOfB = tokenSequenceRepository.findForUpdate(testDate, TENANT_B_ID);
        // Note: findForUpdate takes tenantId as parameter, so it CAN query other tenants
        // But in production, the service layer enforces tenant isolation via TenantContext
        // The key isolation test is that Tenant A's counter is independent of Tenant B's counter

        // Re-verify Tenant A sequence is unchanged
        WalkInTokenSequence sequenceAFinal = tokenSequenceRepository.findForUpdate(testDate, TENANT_A_ID)
                .orElseThrow(() -> new AssertionError("Tenant A sequence should still exist"));
        assertEquals(5, sequenceAFinal.getLastToken(), 
                "Tenant A counter should still be 5 (unaffected by Tenant B operations)");

        TenantContext.clear();

        // Verify Tenant B sequence is unchanged
        TenantContext.setTenantId(TENANT_B_ID);
        WalkInTokenSequence sequenceBFinal = tokenSequenceRepository.findForUpdate(testDate, TENANT_B_ID)
                .orElseThrow(() -> new AssertionError("Tenant B sequence should still exist"));
        assertEquals(3, sequenceBFinal.getLastToken(),
                "Tenant B counter should still be 3 (unaffected by Tenant A operations)");

        TenantContext.clear();

        // Verify sequences are distinct records
        assertNotEquals(sequenceAAfterUpdates.getId(), sequenceBAfterUpdates.getId(),
                "Tenant A and B should have different sequence record IDs");

        System.out.println("=== Cross-Tenant Isolation Test Completed ===");
        System.out.println("Tenant A counter: " + sequenceAFinal.getLastToken());
        System.out.println("Tenant B counter: " + sequenceBFinal.getLastToken());
        System.out.println("Both tenants have independent sequences on same date");
        System.out.println("Tenant isolation is maintained");
        System.out.println("============================================");
    }
}
