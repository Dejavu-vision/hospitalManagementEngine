package com.curamatrix.hsm.service;

import com.curamatrix.hsm.dto.request.TenantRegistrationRequest;
import com.curamatrix.hsm.dto.request.TenantRenewalRequest;
import com.curamatrix.hsm.dto.request.AdminPasswordResetRequest;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantManagementService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeIdGeneratorService employeeIdGeneratorService;
    private final AccessControlService accessControlService;

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

        // Validate dates
        if (request.getSubscriptionEnd() != null && !request.getSubscriptionEnd().isAfter(java.time.LocalDate.now())) {
            throw new RuntimeException("End date Can't be today's date or in the past.");
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
                .settings(request.getSettings() != null ? request.getSettings() : new HashMap<>())
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
                .employeeId(employeeIdGeneratorService.generateEmployeeId(tenant.getId(), RoleName.ROLE_ADMIN))
                .phone(request.getAdminPhone())
                .isActive(true)
                .roles(roles)
                .build();

        admin.setTenantId(tenant.getId());
        userRepository.save(admin);
        
        log.info("Admin user created for tenant {}: {}", tenant.getTenantKey(), admin.getEmail());

        if (request.getAllowedPageKeys() != null && !request.getAllowedPageKeys().isEmpty()) {
            accessControlService.setUserPages(admin.getId(), tenant.getId(), request.getAllowedPageKeys(), admin.getId());
        }
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

        if (request.getSubscriptionStart() != null && request.getSubscriptionEnd() != null) {
            if (request.getSubscriptionEnd().isBefore(request.getSubscriptionStart())) {
                throw new RuntimeException("Subscription end date cannot be before subscription start date.");
            }
        }

        tenant.setHospitalName(request.getHospitalName());
        tenant.setSubscriptionPlan(request.getSubscriptionPlan());
        if (request.getSubscriptionStart() != null) {
            tenant.setSubscriptionStart(request.getSubscriptionStart());
        }
        if (request.getSubscriptionEnd() != null) {
            tenant.setSubscriptionEnd(request.getSubscriptionEnd());
        }
        tenant.setContactEmail(request.getContactEmail());
        tenant.setContactPhone(request.getContactPhone());
        tenant.setAddress(request.getAddress());
        tenant.setLogo(request.getLogo());

        // Update limits based on plan
        SubscriptionPlan plan = SubscriptionPlan.valueOf(request.getSubscriptionPlan());
        tenant.setMaxUsers(plan.getMaxUsers());
        tenant.setMaxPatients(plan.getMaxPatients());

        if (request.getIsActive() != null) {
            tenant.setIsActive(request.getIsActive());
        } else if (tenant.getSubscriptionEnd() != null && tenant.getSubscriptionEnd().isAfter(LocalDate.now())) {
            // Automatically reactivate expired/inactive hospital when subscription end date is extended into the future
            tenant.setIsActive(true);
        }

        if (request.getSettings() != null) {
            tenant.setSettings(request.getSettings());
        }

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

    /**
     * Paginated + filtered tenant listing for Super Admin dashboard.
     */
    public Page<TenantResponse> getTenantsPaginated(Boolean isActive, String plan, Pageable pageable) {
        Page<Tenant> page;
        if (isActive != null && plan != null && !plan.isBlank()) {
            page = tenantRepository.findByIsActiveAndSubscriptionPlan(isActive, plan, pageable);
        } else if (isActive != null) {
            page = tenantRepository.findByIsActive(isActive, pageable);
        } else if (plan != null && !plan.isBlank()) {
            page = tenantRepository.findBySubscriptionPlan(plan, pageable);
        } else {
            page = tenantRepository.findAll(pageable);
        }
        return page.map(this::mapToResponse);
    }

    @Transactional
    public TenantResponse renewSubscription(Long tenantId, TenantRenewalRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found with id: " + tenantId));

        String currentPlanStr = tenant.getSubscriptionPlan();
        String newPlanStr = (request.getSubscriptionPlan() != null && !request.getSubscriptionPlan().isBlank())
                ? request.getSubscriptionPlan().toUpperCase().trim()
                : currentPlanStr;

        SubscriptionPlan newPlan;
        try {
            newPlan = SubscriptionPlan.valueOf(newPlanStr);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid subscription plan: " + newPlanStr);
        }

        LocalDate prevStart = tenant.getSubscriptionStart();
        LocalDate prevEnd = tenant.getSubscriptionEnd();
        LocalDate today = LocalDate.now();

        LocalDate newStart;
        LocalDate newEnd;

        if (request.getSubscriptionEnd() != null) {
            if (!request.getSubscriptionEnd().isAfter(today)) {
                throw new RuntimeException("Subscription end date must be in the future.");
            }
            newEnd = request.getSubscriptionEnd();
            newStart = request.getSubscriptionStart() != null ? request.getSubscriptionStart()
                    : (prevStart != null ? prevStart : today);
        } else {
            int months = (request.getExtensionMonths() != null && request.getExtensionMonths() > 0)
                    ? request.getExtensionMonths()
                    : 12;

            if (request.getSubscriptionStart() != null) {
                newStart = request.getSubscriptionStart();
                newEnd = newStart.plusMonths(months);
            } else if (prevEnd != null && prevEnd.isAfter(today)) {
                // Subscription is active -> extend from existing end date
                newStart = prevStart != null ? prevStart : today;
                newEnd = prevEnd.plusMonths(months);
            } else {
                // Subscription is expired or not set -> start from today
                newStart = today;
                newEnd = today.plusMonths(months);
            }
        }

        if (!newEnd.isAfter(newStart)) {
            throw new RuntimeException("Subscription end date must be after subscription start date.");
        }

        tenant.setSubscriptionPlan(newPlan.name());
        tenant.setSubscriptionStart(newStart);
        tenant.setSubscriptionEnd(newEnd);
        tenant.setMaxUsers(newPlan.getMaxUsers());
        tenant.setMaxPatients(newPlan.getMaxPatients());

        if (Boolean.TRUE.equals(request.getAutoActivate()) || request.getAutoActivate() == null) {
            tenant.setIsActive(true);
        }

        // Store renewal history in settings JSON
        Map<String, Object> settings = tenant.getSettings();
        if (settings == null) {
            settings = new HashMap<>();
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> renewalHistory = (List<Map<String, Object>>) settings.get("renewalHistory");
        if (renewalHistory == null) {
            renewalHistory = new ArrayList<>();
        } else {
            renewalHistory = new ArrayList<>(renewalHistory);
        }

        Map<String, Object> record = new HashMap<>();
        record.put("renewedAt", LocalDateTime.now().toString());
        record.put("previousPlan", currentPlanStr);
        record.put("newPlan", newPlan.name());
        record.put("previousStart", prevStart != null ? prevStart.toString() : null);
        record.put("previousEnd", prevEnd != null ? prevEnd.toString() : null);
        record.put("newStart", newStart.toString());
        record.put("newEnd", newEnd.toString());
        record.put("extensionMonths", request.getExtensionMonths());
        if (request.getAmountPaid() != null) {
            record.put("amountPaid", request.getAmountPaid().toString());
        }
        if (request.getPaymentReference() != null && !request.getPaymentReference().isBlank()) {
            record.put("paymentReference", request.getPaymentReference().trim());
        }
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            record.put("notes", request.getNotes().trim());
        }

        renewalHistory.add(record);
        settings.put("renewalHistory", renewalHistory);
        tenant.setSettings(settings);

        tenant = tenantRepository.save(tenant);
        log.info("Tenant {} (ID: {}) subscription renewed to plan {} valid until {}",
                tenant.getTenantKey(), tenant.getId(), tenant.getSubscriptionPlan(), tenant.getSubscriptionEnd());

        return mapToResponse(tenant);
    }




    @Transactional
    public void resetTenantAdminPassword(Long tenantId, AdminPasswordResetRequest request) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found with id: " + tenantId));

        List<User> tenantUsers = userRepository.findAllByTenantId(tenantId);
        User adminUser = tenantUsers.stream()
                .filter(user -> user.getRoles().stream().anyMatch(role -> role.getName() == RoleName.ROLE_ADMIN))
                .filter(user -> request.getAdminEmail() == null || request.getAdminEmail().isBlank()
                        || user.getEmail().equalsIgnoreCase(request.getAdminEmail()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Admin user not found for tenant id: " + tenantId));

        adminUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(adminUser);

        log.info("Admin password reset for tenant: {}, admin email: {}", tenantId, adminUser.getEmail());
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
                .settings(tenant.getSettings())
                .createdAt(tenant.getCreatedAt())
                .currentUsers((int) userCount)
                .currentPatients((int) patientCount)
                .build();
    }
}
