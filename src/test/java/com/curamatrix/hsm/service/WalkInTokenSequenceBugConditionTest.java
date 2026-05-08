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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property 1: Bug Condition - Token Sequence INSERT Fails with Schema Mismatch
 * 
 * **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
 * **DO NOT attempt to fix the test or the code when it fails**
 * 
 * This test encodes the expected behavior - it will validate the fix when it passes after implementation.
 * 
 * GOAL: Surface counterexamples that demonstrate the bug exists (SQL error: "Field 'counter' doesn't have a default value")
 * 
 * Test implementation: Create integration test that simulates walk-in appointment creation flow 
 * for a NEW date/tenant (no existing token sequence).
 * 
 * EXPECTED OUTCOME on UNFIXED code: Test FAILS with SQL error "Field 'counter' doesn't have a default value"
 * 
 * Validates: Requirements 1.1, 1.2, 1.3
 */
@SpringBootTest
@Transactional
class WalkInTokenSequenceBugConditionTest {

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

    private static final Long TEST_TENANT_ID = 999L;
    private Patient testPatient;
    private Doctor testDoctor;
    private User testReceptionist;

    @BeforeEach
    void setUp() {
        // Set tenant context for the test
        TenantContext.setTenantId(TEST_TENANT_ID);

        // Create test tenant
        Tenant tenant = Tenant.builder()
                .tenantKey("test-hospital-bug-condition")
                .hospitalName("Test Hospital for Bug Condition")
                .contactEmail("test@hospital.com")
                .contactPhone("1234567890")
                .address("Test Address")
                .subscriptionPlan("BASIC")
                .subscriptionStart(LocalDate.now())
                .subscriptionEnd(LocalDate.now().plusYears(1))
                .isActive(true)
                .build();
        tenant = tenantRepository.save(tenant);

        // Create test department
        Department department = Department.builder()
                .name("General Medicine")
                .description("General Medicine Department")
                .isActive(true)
                .build();
        department = departmentRepository.save(department);

        // Create test doctor user
        User doctorUser = User.builder()
                .email("doctor.bug@test.com")
                .password("password")
                .fullName("Dr. Test Doctor")
                .phone("9876543210")
                .isActive(true)
                .build();
        doctorUser.setTenantId(TEST_TENANT_ID);
        doctorUser = userRepository.save(doctorUser);

        // Create test doctor
        testDoctor = Doctor.builder()
                .user(doctorUser)
                .department(department)
                .licenseNumber("DOC-BUG-001")
                .consultationFee(new java.math.BigDecimal("500.00"))
                .experienceYears(10)
                .qualification("MBBS, MD")
                .build();
        testDoctor = doctorRepository.save(testDoctor);

        // Create test receptionist user
        testReceptionist = User.builder()
                .email("receptionist.bug@test.com")
                .password("password")
                .fullName("Test Receptionist")
                .phone("9876543211")
                .isActive(true)
                .build();
        testReceptionist.setTenantId(TEST_TENANT_ID);
        testReceptionist = userRepository.save(testReceptionist);

        // Set up security context with receptionist user
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        testReceptionist.getEmail(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_RECEPTIONIST"))
                )
        );

        // Create test patient
        testPatient = Patient.builder()
                .patientCode("PAT-BUG-001")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender(com.curamatrix.hsm.enums.Gender.MALE)
                .phone("9999999999")
                .email("john.doe.bug@test.com")
                .address("Test Address")
                .bloodGroup(com.curamatrix.hsm.enums.BloodGroup.O_POSITIVE)
                .build();
        testPatient.setTenantId(TEST_TENANT_ID);
        testPatient = patientRepository.save(testPatient);

        // CRITICAL: Ensure NO existing token sequence for today and this tenant
        // This ensures we trigger the INSERT path (not UPDATE path)
        LocalDate today = LocalDate.now();
        tokenSequenceRepository.findForUpdate(today, TEST_TENANT_ID, testDoctor.getId())
                .ifPresent(seq -> tokenSequenceRepository.delete(seq));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    /**
     * Bug Condition Test: Creating a walk-in appointment for a NEW date/tenant combination
     * should successfully create a new token sequence record.
     * 
     * On UNFIXED code (entity has @Column(name = "last_token")):
     * - Hibernate generates: INSERT INTO walk_in_token_sequence (appointment_date, tenant_id, last_token, ...) VALUES (...)
     * - Database expects: counter column, not last_token
     * - Result: SQL error "Field 'counter' doesn't have a default value"
     * 
     * On FIXED code (entity has @Column(name = "counter")):
     * - Hibernate generates: INSERT INTO walk_in_token_sequence (appointment_date, tenant_id, counter, ...) VALUES (...)
     * - Database receives: correct column name
     * - Result: Successful INSERT with counter = 1
     * 
     * **Validates: Requirements 1.1, 1.2, 1.3, 2.1, 2.2, 2.3**
     */
    @Test
    void testWalkInCreationForNewDateTenantCreatesTokenSequence() {
        // Arrange
        WalkInRequest request = new WalkInRequest();
        request.setPatientId(testPatient.getId());
        request.setDoctorId(testDoctor.getId());
        request.setNotes("First walk-in of the day");
        request.setPayNow(false);
        request.setFollowUp(false);
        request.setCounter("A");

        LocalDate today = LocalDate.now();

        // Verify no existing token sequence (precondition)
        assertTrue(tokenSequenceRepository.findForUpdate(today, TEST_TENANT_ID, testDoctor.getId()).isEmpty(),
                "Precondition failed: Token sequence should not exist for today and test tenant");

        // Act - This should trigger the bug on unfixed code
        AppointmentResponse response = appointmentService.createWalkIn(request);

        // Assert - Expected behavior (will pass after fix)
        assertNotNull(response, "Appointment response should not be null");
        assertNotNull(response.getId(), "Appointment ID should be assigned");
        assertEquals(1, response.getTokenNumber(), "First token of the day should be 1");

        // Verify token sequence record was created in database
        WalkInTokenSequence sequence = tokenSequenceRepository.findForUpdate(today, TEST_TENANT_ID, testDoctor.getId())
                .orElseThrow(() -> new AssertionError("Token sequence record should exist after walk-in creation"));

        assertEquals(today, sequence.getAppointmentDate(), "Sequence should be for today's date");
        assertEquals(TEST_TENANT_ID, sequence.getTenantId(), "Sequence should be for test tenant");
        assertEquals(1, sequence.getLastToken(), "Counter should be 1 after first walk-in");

        // Document the expected SQL statement (for manual verification in logs)
        // Expected on FIXED code: INSERT INTO walk_in_token_sequence (..., counter, ...) VALUES (...)
        // Expected on UNFIXED code: INSERT INTO walk_in_token_sequence (..., last_token, ...) VALUES (...)
        System.out.println("=== Bug Condition Test Completed ===");
        System.out.println("Check Hibernate SQL logs for INSERT statement");
        System.out.println("FIXED code should show: INSERT INTO walk_in_token_sequence (..., counter, ...)");
        System.out.println("UNFIXED code would show: INSERT INTO walk_in_token_sequence (..., last_token, ...)");
        System.out.println("====================================");
    }

    /**
     * Additional test: Verify subsequent walk-in increments the counter correctly
     * This test should PASS even on unfixed code (UPDATE path works)
     */
    @Test
    void testSubsequentWalkInIncrementsCounter() {
        // Arrange - Create first walk-in to establish sequence
        WalkInRequest firstRequest = new WalkInRequest();
        firstRequest.setPatientId(testPatient.getId());
        firstRequest.setDoctorId(testDoctor.getId());
        firstRequest.setNotes("First walk-in");
        firstRequest.setPayNow(false);
        firstRequest.setFollowUp(false);
        firstRequest.setCounter("A");

        AppointmentResponse firstResponse = appointmentService.createWalkIn(firstRequest);
        assertEquals(1, firstResponse.getTokenNumber(), "First token should be 1");

        // Act - Create second walk-in (UPDATE path)
        WalkInRequest secondRequest = new WalkInRequest();
        secondRequest.setPatientId(testPatient.getId());
        secondRequest.setDoctorId(testDoctor.getId());
        secondRequest.setNotes("Second walk-in");
        secondRequest.setPayNow(false);
        secondRequest.setFollowUp(false);
        secondRequest.setCounter("B");

        AppointmentResponse secondResponse = appointmentService.createWalkIn(secondRequest);

        // Assert
        assertEquals(2, secondResponse.getTokenNumber(), "Second token should be 2");

        // Verify sequence counter in database
        LocalDate today = LocalDate.now();
        WalkInTokenSequence sequence = tokenSequenceRepository.findForUpdate(today, TEST_TENANT_ID, testDoctor.getId())
                .orElseThrow(() -> new AssertionError("Token sequence should exist"));

        assertEquals(2, sequence.getLastToken(), "Counter should be 2 after second walk-in");
    }
}
