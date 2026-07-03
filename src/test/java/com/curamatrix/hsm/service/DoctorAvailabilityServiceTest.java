package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.entity.Department;
import com.curamatrix.hsm.entity.Doctor;
import com.curamatrix.hsm.entity.DoctorAvailability;
import com.curamatrix.hsm.entity.User;
import com.curamatrix.hsm.enums.DoctorStatus;
import com.curamatrix.hsm.repository.DoctorAvailabilityRepository;
import com.curamatrix.hsm.repository.DoctorRepository;
import com.curamatrix.hsm.repository.UserRepository;
import com.curamatrix.hsm.repository.DoctorStatusLogRepository;
import com.curamatrix.hsm.repository.AppointmentRepository;
import com.curamatrix.hsm.repository.AppointmentStatusLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug condition exploration tests for DoctorAvailabilityService.getTodayAvailability().
 *
 * BUG: doctor.getUser().getId() and doctor.getUser().getFullName() are called without
 * null guards. When any Doctor in the tenant has a null user association, this throws
 * NullPointerException → HTTP 500.
 *
 * These tests are EXPECTED TO FAIL on unfixed code — failure confirms the bug exists.
 * After the fix is applied, all tests should PASS.
 *
 * Validates: Requirements 1.1, 1.2
 */
@ExtendWith(MockitoExtension.class)
class DoctorAvailabilityServiceTest {

    @Mock
    private DoctorAvailabilityRepository availabilityRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DoctorStatusLogRepository doctorStatusLogRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentStatusLogRepository appointmentStatusLogRepository;
    @Mock
    private QueueEventService queueEventService;

    private DoctorAvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new DoctorAvailabilityService(
                availabilityRepository,
                doctorRepository,
                userRepository,
                doctorStatusLogRepository,
                appointmentRepository,
                appointmentStatusLogRepository,
                queueEventService
        );
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Creates a Doctor with user = null (the bug condition). */
    private Doctor doctorWithNullUser(Long id) {
        Doctor d = new Doctor();
        d.setId(id);
        // user is intentionally left null — this is the bug condition
        return d;
    }

    /** Creates a Doctor with a valid User association. */
    private Doctor doctorWithUser(Long id, String fullName) {
        User user = new User();
        user.setId(id * 10);
        user.setFullName(fullName);
        Doctor d = new Doctor();
        d.setId(id);
        d.setUser(user);
        return d;
    }

    // -------------------------------------------------------------------------
    // BUG CONDITION EXPLORATION TESTS
    //
    // Each test asserts assertDoesNotThrow() — the DESIRED behavior after the fix.
    // On UNFIXED code these tests FAIL because getTodayAvailability() throws NPE.
    // On FIXED code these tests PASS because null-user doctors are skipped.
    // -------------------------------------------------------------------------

    /**
     * BUG CONDITION EXPLORATION TEST — Property 1
     *
     * Single null-user doctor in the tenant.
     * On UNFIXED code: NullPointerException is thrown → test FAILS (confirms bug).
     * On FIXED code: empty list returned, no exception → test PASSES.
     *
     * Validates: Requirements 1.1, 1.2
     */
    @Test
    void bugCondition_singleNullUserDoctor_shouldNotThrowNPE() {
        Doctor nullUserDoctor = doctorWithNullUser(1L);
        when(doctorRepository.findByTenantId(1L)).thenReturn(List.of(nullUserDoctor));
        // No availability stub needed — null-user doctor is filtered before repo is called

        // On UNFIXED code: throws NullPointerException at doctor.getUser().getId()
        // On FIXED code: returns empty list (null-user doctor is skipped)
        var result = assertDoesNotThrow(() -> service.getTodayAvailability(),
                "getTodayAvailability() must not throw NPE when a doctor has null user");
        assertTrue(result.isEmpty(), "Null-user doctor should be skipped; result should be empty");
    }

    /**
     * BUG CONDITION EXPLORATION TEST — null-user doctor first in mixed list.
     *
     * The null-user doctor appears before the valid doctor in the list.
     * On UNFIXED code: NPE thrown before the valid doctor is processed → test FAILS.
     * On FIXED code: only the valid doctor appears in the result → test PASSES.
     *
     * Validates: Requirements 1.1, 1.2
     */
    @Test
    void bugCondition_mixedList_nullUserDoctorFirst_shouldNotThrowNPE() {
        Doctor nullUserDoctor = doctorWithNullUser(1L);
        Doctor validDoctor = doctorWithUser(2L, "Dr. Smith");
        when(doctorRepository.findByTenantId(1L)).thenReturn(List.of(nullUserDoctor, validDoctor));
        when(availabilityRepository.findByDoctorIdAndAvailabilityDateAndTenantId(anyLong(), any(), anyLong()))
                .thenReturn(Optional.empty());

        var result = assertDoesNotThrow(() -> service.getTodayAvailability(),
                "getTodayAvailability() must not throw NPE when null-user doctor is first in list");
        assertEquals(1, result.size(), "Only the valid-user doctor should appear in the result");
        assertEquals(2L, result.get(0).getDoctorId(), "The valid doctor (id=2) should be in the result");
    }

    /**
     * BUG CONDITION EXPLORATION TEST — null-user doctor last in mixed list.
     *
     * The null-user doctor appears after the valid doctor in the list.
     * On UNFIXED code: NPE thrown after the valid doctor is processed → test FAILS.
     * On FIXED code: only the valid doctor appears in the result → test PASSES.
     *
     * Validates: Requirements 1.1, 1.2
     */
    @Test
    void bugCondition_mixedList_nullUserDoctorLast_shouldNotThrowNPE() {
        Doctor validDoctor = doctorWithUser(1L, "Dr. Jones");
        Doctor nullUserDoctor = doctorWithNullUser(2L);
        when(doctorRepository.findByTenantId(1L)).thenReturn(List.of(validDoctor, nullUserDoctor));
        when(availabilityRepository.findByDoctorIdAndAvailabilityDateAndTenantId(anyLong(), any(), anyLong()))
                .thenReturn(Optional.empty());

        var result = assertDoesNotThrow(() -> service.getTodayAvailability(),
                "getTodayAvailability() must not throw NPE when null-user doctor is last in list");
        assertEquals(1, result.size(), "Only the valid-user doctor should appear in the result");
        assertEquals(1L, result.get(0).getDoctorId(), "The valid doctor (id=1) should be in the result");
    }

    /**
     * BUG CONDITION EXPLORATION TEST — all doctors have null users.
     *
     * Every doctor in the tenant has a null user association.
     * On UNFIXED code: NPE thrown immediately → test FAILS.
     * On FIXED code: empty list returned → test PASSES.
     *
     * Validates: Requirements 1.1, 1.2
     */
    @Test
    void bugCondition_allNullUserDoctors_shouldReturnEmptyList() {
        when(doctorRepository.findByTenantId(1L)).thenReturn(
                List.of(doctorWithNullUser(1L), doctorWithNullUser(2L), doctorWithNullUser(3L)));
        // No availability stub needed — all null-user doctors are filtered before repo is called

        var result = assertDoesNotThrow(() -> service.getTodayAvailability(),
                "getTodayAvailability() must not throw NPE when all doctors have null users");
        assertTrue(result.isEmpty(), "All null-user doctors should be skipped; result should be empty");
    }

    // -------------------------------------------------------------------------
    // PRESERVATION TESTS — valid-user doctors are unaffected by the fix
    // -------------------------------------------------------------------------

    @Test
    void preservation_allValidDoctors_withAvailabilityRecord_returnsCorrectFields() {
        User user = new User();
        user.setId(10L);
        user.setFullName("Dr. Patel");
        Doctor doctor = new Doctor();
        doctor.setId(1L);
        doctor.setUser(user);

        DoctorAvailability avail = new DoctorAvailability();
        avail.setDoctor(doctor);
        avail.setAvailabilityDate(LocalDate.now());
        avail.setIsPresent(true);
        avail.setStatus(DoctorStatus.AVAILABLE);

        when(doctorRepository.findByTenantId(1L)).thenReturn(List.of(doctor));
        when(availabilityRepository.findByDoctorIdAndAvailabilityDateAndTenantId(1L, LocalDate.now(), 1L))
                .thenReturn(Optional.of(avail));

        var result = service.getTodayAvailability();

        assertEquals(1, result.size());
        var resp = result.get(0);
        assertEquals(1L, resp.getDoctorId());
        assertEquals(10L, resp.getUserId());
        assertEquals("Dr. Patel", resp.getDoctorName());
        assertTrue(resp.getIsPresent());
        assertEquals(DoctorStatus.AVAILABLE, resp.getStatus());
    }

    @Test
    void preservation_allValidDoctors_noAvailabilityRecord_returnsDefaultOffDuty() {
        User user = new User();
        user.setId(20L);
        user.setFullName("Dr. Kumar");
        Doctor doctor = new Doctor();
        doctor.setId(2L);
        doctor.setUser(user);

        when(doctorRepository.findByTenantId(1L)).thenReturn(List.of(doctor));
        when(availabilityRepository.findByDoctorIdAndAvailabilityDateAndTenantId(anyLong(), any(), anyLong()))
                .thenReturn(Optional.empty());

        var result = service.getTodayAvailability();

        assertEquals(1, result.size());
        var resp = result.get(0);
        assertEquals(2L, resp.getDoctorId());
        assertEquals("Dr. Kumar", resp.getDoctorName());
        assertFalse(resp.getIsPresent());
        assertEquals(DoctorStatus.OFFLINE, resp.getStatus());
    }

    @Test
    void preservation_validDoctorWithNullDepartment_departmentNameIsNull() {
        User user = new User();
        user.setId(30L);
        user.setFullName("Dr. Singh");
        Doctor doctor = new Doctor();
        doctor.setId(3L);
        doctor.setUser(user);
        // department is null

        when(doctorRepository.findByTenantId(1L)).thenReturn(List.of(doctor));
        when(availabilityRepository.findByDoctorIdAndAvailabilityDateAndTenantId(anyLong(), any(), anyLong()))
                .thenReturn(Optional.empty());

        var result = service.getTodayAvailability();

        assertEquals(1, result.size());
        assertNull(result.get(0).getDepartmentName());
    }

    @Test
    void preservation_addingNullUserDoctorDoesNotAffectValidDoctors() {
        User user = new User();
        user.setId(40L);
        user.setFullName("Dr. Sharma");
        Doctor validDoctor = new Doctor();
        validDoctor.setId(4L);
        validDoctor.setUser(user);

        Doctor nullUserDoctor = new Doctor();
        nullUserDoctor.setId(5L);
        // user is null

        when(doctorRepository.findByTenantId(1L)).thenReturn(List.of(validDoctor, nullUserDoctor));
        when(availabilityRepository.findByDoctorIdAndAvailabilityDateAndTenantId(anyLong(), any(), anyLong()))
                .thenReturn(Optional.empty());

        var result = service.getTodayAvailability();

        // Only the valid doctor should appear; null-user doctor is silently skipped
        assertEquals(1, result.size());
        assertEquals(4L, result.get(0).getDoctorId());
        assertEquals("Dr. Sharma", result.get(0).getDoctorName());
    }
}
