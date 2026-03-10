package com.curamatrix.hsm.service;

import com.curamatrix.hsm.dto.request.TenantRegistrationRequest;
import com.curamatrix.hsm.dto.response.TenantResponse;
import com.curamatrix.hsm.entity.Role;
import com.curamatrix.hsm.entity.Tenant;
import com.curamatrix.hsm.entity.User;
import com.curamatrix.hsm.enums.RoleName;
import com.curamatrix.hsm.enums.SubscriptionPlan;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantManagementService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TenantResponse registerTenant(TenantRegistrationRequest request) {
        // Validate tenant key uniqueness
        if (tenantRepository.existsByTenantKey(request.getTenantKey())) {
            throw new RuntimeException("Tenant key already exists: " + request.getTenantKey());
        }

        // Validate subscription plan
        SubscriptionPlan plan;
        try {
            plan = SubscriptionPlan.valueOf(request.getSubscriptionPlan());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid subscription plan: " + request.getSubscriptionPlan());
        }

        // Create tenant
        Tenant tenant = Tenant.builder()
                .tenantKey(request.getTenantKey())
                .hospitalName(request.getHospitalName())
                .subscriptionPlan(request.getSubscriptionPlan())
                .subscriptionStart(request.getSubscriptionStart())
                .subscriptionEnd(request.getSubscriptionEnd())
                .isActive(true)
                .maxUsers(plan.getMaxUsers())
                .maxPatients(plan.getMaxPatients())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .address(request.getAddress())
                .logo(request.getLogo())
                .settings(new HashMap<>())
                .build();

        tenant = tenantRepository.save(tenant);
        log.info("Tenant created: {} (ID: {})", tenant.getTenantKey(), tenant.getId());

        // Create admin user for this tenant
        createAdminUser(tenant, request);

        return mapToResponse(tenant);
    }

    private void createAdminUser(Tenant tenant, TenantRegistrationRequest request) {
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                .orElseThrow(() -> new RuntimeException("Admin role not found"));

        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);

        User admin = User.builder()
                .email(request.getAdminEmail())
                .password(passwordEncoder.encode(request.getAdminPassword()))
                .fullName(request.getAdminFullName())
                .phone(request.getAdminPhone())
                .isActive(true)
                .roles(roles)
                .build();

        admin.setTenantId(tenant.getId());
        userRepository.save(admin);
        
        log.info("Admin user created for tenant {}: {}", tenant.getTenantKey(), admin.getEmail());
    }

    public List<TenantResponse> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TenantResponse getTenantById(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant not found with id: " + id));
        return mapToResponse(tenant);
    }

    @Transactional
    public TenantResponse updateTenant(Long id, TenantRegistrationRequest request) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant not found with id: " + id));

        tenant.setHospitalName(request.getHospitalName());
        tenant.setSubscriptionPlan(request.getSubscriptionPlan());
        tenant.setSubscriptionStart(request.getSubscriptionStart());
        tenant.setSubscriptionEnd(request.getSubscriptionEnd());
        tenant.setContactEmail(request.getContactEmail());
        tenant.setContactPhone(request.getContactPhone());
        tenant.setAddress(request.getAddress());
        tenant.setLogo(request.getLogo());

        // Update limits based on plan
        SubscriptionPlan plan = SubscriptionPlan.valueOf(request.getSubscriptionPlan());
        tenant.setMaxUsers(plan.getMaxUsers());
        tenant.setMaxPatients(plan.getMaxPatients());

        tenant = tenantRepository.save(tenant);
        log.info("Tenant updated: {}", tenant.getTenantKey());

        return mapToResponse(tenant);
    }

    @Transactional
    public void suspendTenant(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant not found with id: " + id));
        tenant.setIsActive(false);
        tenantRepository.save(tenant);
        log.info("Tenant suspended: {}", tenant.getTenantKey());
    }

    @Transactional
    public void activateTenant(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant not found with id: " + id));
        tenant.setIsActive(true);
        tenantRepository.save(tenant);
        log.info("Tenant activated: {}", tenant.getTenantKey());
    }

    public Object getTenantStats(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant not found with id: " + id));

        long userCount = userRepository.countByTenantId(id);
        long patientCount = patientRepository.countByTenantId(id);

        Map<String, Object> stats = new HashMap<>();
        stats.put("tenantId", tenant.getId());
        stats.put("tenantKey", tenant.getTenantKey());
        stats.put("hospitalName", tenant.getHospitalName());
        stats.put("currentUsers", userCount);
        stats.put("maxUsers", tenant.getMaxUsers());
        stats.put("currentPatients", patientCount);
        stats.put("maxPatients", tenant.getMaxPatients());
        stats.put("subscriptionPlan", tenant.getSubscriptionPlan());
        stats.put("subscriptionExpiry", tenant.getSubscriptionEnd());
        stats.put("isActive", tenant.getIsActive());

        return stats;
    }

    private TenantResponse mapToResponse(Tenant tenant) {
        long userCount = userRepository.countByTenantId(tenant.getId());
        long patientCount = patientRepository.countByTenantId(tenant.getId());

        return TenantResponse.builder()
                .id(tenant.getId())
                .tenantKey(tenant.getTenantKey())
                .hospitalName(tenant.getHospitalName())
                .subscriptionPlan(tenant.getSubscriptionPlan())
                .subscriptionStart(tenant.getSubscriptionStart())
                .subscriptionEnd(tenant.getSubscriptionEnd())
                .isActive(tenant.getIsActive())
                .maxUsers(tenant.getMaxUsers())
                .maxPatients(tenant.getMaxPatients())
                .contactEmail(tenant.getContactEmail())
                .contactPhone(tenant.getContactPhone())
                .address(tenant.getAddress())
                .logo(tenant.getLogo())
                .createdAt(tenant.getCreatedAt())
                .currentUsers((int) userCount)
                .currentPatients((int) patientCount)
                .build();
    }
}
