package com.curamatrix.hsm.service;

import com.curamatrix.hsm.dto.request.TenantRegistrationRequest;
import com.curamatrix.hsm.dto.response.TenantResponse;
import com.curamatrix.hsm.entity.Tenant;
import com.curamatrix.hsm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TenantSubscriptionUpdateTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmployeeIdGeneratorService employeeIdGeneratorService;

    @Mock
    private AccessControlService accessControlService;

    @InjectMocks
    private TenantManagementService tenantManagementService;

    private Tenant existingTenant;

    @BeforeEach
    void setUp() {
        existingTenant = Tenant.builder()
                .id(1L)
                .tenantKey("apollo-mumbai")
                .hospitalName("Apollo Mumbai")
                .subscriptionPlan("STANDARD")
                .subscriptionStart(LocalDate.now().minusMonths(6))
                .subscriptionEnd(LocalDate.now().minusDays(5)) // Expired
                .isActive(false)
                .maxUsers(50)
                .maxPatients(1000)
                .contactEmail("contact@apollo.com")
                .build();
    }

    @Test
    void testUpdateTenant_IncreasesSubscriptionEndDateAndReactivates() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(existingTenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.countByTenantId(1L)).thenReturn(10L);
        when(patientRepository.countByTenantId(1L)).thenReturn(50L);

        LocalDate newStart = LocalDate.now().minusMonths(6);
        LocalDate newEnd = LocalDate.now().plusYears(1); // Extended subscription

        TenantRegistrationRequest request = new TenantRegistrationRequest();
        request.setTenantKey("apollo-mumbai");
        request.setHospitalName("Apollo Mumbai Updated");
        request.setSubscriptionPlan("PREMIUM");
        request.setSubscriptionStart(newStart);
        request.setSubscriptionEnd(newEnd);
        request.setContactEmail("contact@apollo.com");

        TenantResponse response = tenantManagementService.updateTenant(1L, request);

        assertNotNull(response);
        assertEquals("Apollo Mumbai Updated", response.getHospitalName());
        assertEquals("PREMIUM", response.getSubscriptionPlan());
        assertEquals(newStart, response.getSubscriptionStart());
        assertEquals(newEnd, response.getSubscriptionEnd());
        assertTrue(response.getIsActive(), "Expired tenant should be auto-reactivated when subscriptionEnd is extended to the future");
    }

    @Test
    void testUpdateTenant_ThrowsExceptionWhenEndDateBeforeStartDate() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(existingTenant));

        TenantRegistrationRequest request = new TenantRegistrationRequest();
        request.setHospitalName("Apollo Mumbai");
        request.setSubscriptionPlan("STANDARD");
        request.setSubscriptionStart(LocalDate.now().plusMonths(2));
        request.setSubscriptionEnd(LocalDate.now().plusMonths(1)); // Before start date

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                tenantManagementService.updateTenant(1L, request)
        );

        assertTrue(ex.getMessage().contains("Subscription end date cannot be before subscription start date"));
        verify(tenantRepository, never()).save(any(Tenant.class));
    }
}
