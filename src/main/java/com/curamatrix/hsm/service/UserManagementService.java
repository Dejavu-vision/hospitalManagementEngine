package com.curamatrix.hsm.service;

import com.curamatrix.hsm.dto.request.CreateUserRequest;
import com.curamatrix.hsm.dto.request.UpdateUserRequest;
import com.curamatrix.hsm.dto.response.UserResponse;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.RoleName;
import com.curamatrix.hsm.enums.Shift;
import com.curamatrix.hsm.enums.SubscriptionPlan;
import com.curamatrix.hsm.exception.DuplicateResourceException;
import com.curamatrix.hsm.exception.QuotaExceededException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.*;
import com.curamatrix.hsm.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DoctorRepository doctorRepository;
    private final ReceptionistRepository receptionistRepository;
    private final DepartmentRepository departmentRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessControlService accessControlService;
    private final EmployeeIdGeneratorService employeeIdGeneratorService;

    // ─── Create ──────────────────────────────────────────────────

    @Transactional
    public UserResponse createUser(CreateUserRequest request, Long targetTenantId) {
        Role role = roleRepository.findByName(request.getRole())
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", request.getRole()));

        Set<Role> roles = new HashSet<>();
        roles.add(role);

        Long tenantId = targetTenantId != null ? targetTenantId : TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is missing. Please login again.");
        }

        // ─── Quota enforcement: check user limit ────────────────
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));

        SubscriptionPlan plan = SubscriptionPlan.valueOf(tenant.getSubscriptionPlan());
        if (!plan.isUnlimited("users")) {
            long currentUsers = userRepository.countByTenantId(tenantId);
            if (currentUsers >= tenant.getMaxUsers()) {
                throw new QuotaExceededException(
                        "User creation limit reached. Your plan allows a maximum of " +
                        tenant.getMaxUsers() + " users. " +
                        "Please upgrade your subscription to add more users.");
            }
        }

        Optional<User> existingOpt = userRepository.findByEmail(request.getEmail());
        if (existingOpt.isPresent()) {
            return reactivateOrRejectExistingUser(existingOpt.get(), request, role, tenantId);
        }

        String generatedEmployeeId = employeeIdGeneratorService.generateEmployeeId(tenantId, request.getRole());

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .employeeId(generatedEmployeeId)
                .phone(request.getPhone())
                .isActive(true)
                .roles(roles)
                .build();
        user.setTenantId(tenantId);

        user = userRepository.save(user);
        log.info("User created: {} with role: {}", user.getEmail(), request.getRole());

        // Apply extra page access (if provided during creation)
        Set<String> extraPages = request.getAllowedPageKeys();
        if (extraPages != null && !extraPages.isEmpty()) {
            accessControlService.setUserPages(user.getId(), user.getTenantId(), extraPages, user.getId());
        }

        // Create role-specific profile
        if (request.getRole() == RoleName.ROLE_DOCTOR) {
            createDoctorProfile(user, request);
        } else if (request.getRole() == RoleName.ROLE_RECEPTIONIST) {
            createReceptionistProfile(user, request);
        }

        return mapToResponse(user);
    }

    private UserResponse reactivateOrRejectExistingUser(User existingUser,
            CreateUserRequest request,
            Role role,
            Long tenantId) {
        if (Boolean.TRUE.equals(existingUser.getIsActive())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }
        if (!tenantId.equals(existingUser.getTenantId())) {
            throw new DuplicateResourceException(
                    "Email already exists in another hospital: " + request.getEmail());
        }

        existingUser.setIsActive(true);
        existingUser.setFullName(request.getFullName());
        existingUser.setPhone(request.getPhone());
        existingUser.setPassword(passwordEncoder.encode(request.getPassword()));
        existingUser.setRoles(Set.of(role));

        if (existingUser.getEmployeeId() == null || existingUser.getEmployeeId().isBlank()) {
            existingUser.setEmployeeId(employeeIdGeneratorService.generateEmployeeId(tenantId, request.getRole()));
        }

        User user = userRepository.save(existingUser);
        log.info("Reactivated user: {} with role: {}", user.getEmail(), request.getRole());

        if (request.getRole() == RoleName.ROLE_DOCTOR) {
            createOrUpdateDoctorProfile(user, request);
        } else if (request.getRole() == RoleName.ROLE_RECEPTIONIST) {
            createOrUpdateReceptionistProfile(user, request);
        }

        return mapToResponse(user);
    }

    // ─── Update ──────────────────────────────────────────────────

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        user = userRepository.save(user);
        log.info("User updated: {}", user.getEmail());
        return mapToResponse(user);
    }

    // ─── Role Management ─────────────────────────────────────────

    @Transactional
    public UserResponse addRole(Long userId, RoleName roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", roleName));

        user.getRoles().add(role);
        user = userRepository.save(user);
        log.info("Role {} added to user {}", roleName, user.getEmail());
        return mapToResponse(user);
    }

    @Transactional
    public UserResponse removeRole(Long userId, RoleName roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        boolean removed = user.getRoles().removeIf(r -> r.getName() == roleName);
        if (!removed) {
            throw new ResourceNotFoundException("User does not have role: " + roleName);
        }
        if (user.getRoles().isEmpty()) {
            throw new IllegalArgumentException("Cannot remove the last role. A user must have at least one role.");
        }

        user = userRepository.save(user);
        log.info("Role {} removed from user {}", roleName, user.getEmail());
        return mapToResponse(user);
    }

    @Transactional
    public UserResponse setRoles(Long userId, Set<RoleName> roleNames) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (roleNames == null || roleNames.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required.");
        }

        Set<Role> newRoles = new HashSet<>();
        for (RoleName rn : roleNames) {
            Role role = roleRepository.findByName(rn)
                    .orElseThrow(() -> new ResourceNotFoundException("Role", "name", rn));
            newRoles.add(role);
        }

        user.setRoles(newRoles);
        user = userRepository.save(user);
        log.info("Roles set for user {}: {}", user.getEmail(), roleNames);
        return mapToResponse(user);
    }

    // ─── Activate / Deactivate ───────────────────────────────────

    @Transactional
    public void deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setIsActive(false);
        userRepository.save(user);
        log.info("User deactivated: {}", user.getEmail());
    }

    @Transactional
    public void activateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setIsActive(true);
        userRepository.save(user);
        log.info("User activated: {}", user.getEmail());
    }

    // ─── Delete (soft: deactivate + clear pages) ─────────────────

    @Transactional
    public void deleteUser(Long id, Long actorUserId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // Clear user-specific page overrides
        accessControlService.resetUserPages(user.getId(), user.getTenantId(), actorUserId);

        // Soft-delete: deactivate
        user.setIsActive(false);
        userRepository.save(user);
        log.info("User soft-deleted (deactivated + pages cleared): {}", user.getEmail());
    }

    // ─── Read ────────────────────────────────────────────────────

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all users belonging to a specific tenant (hospital).
     */
    public List<UserResponse> getUsersByTenantId(Long tenantId) {
        return userRepository.findAllByTenantId(tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return mapToResponse(user);
    }

    // ─── Audit Log ───────────────────────────────────────────────

    public List<UserAccessAudit> getAuditLog(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return accessControlService.getAuditLog(userId, user.getTenantId());
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private void createDoctorProfile(User user, CreateUserRequest request) {
        createOrUpdateDoctorProfile(user, request);
    }

    private void createOrUpdateDoctorProfile(User user, CreateUserRequest request) {
        if (request.getDepartmentId() == null ||
                request.getLicenseNumber() == null || request.getConsultationFee() == null) {
            throw new IllegalArgumentException(
                    "Doctor profile requires: departmentId, licenseNumber, consultationFee");
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", request.getDepartmentId()));

        Doctor doctor = doctorRepository.findByUserId(user.getId()).orElse(Doctor.builder().user(user).build());
        doctor.setDepartment(department);
        doctor.setLicenseNumber(request.getLicenseNumber());
        doctor.setQualification(request.getQualification());
        doctor.setExperienceYears(request.getExperienceYears());
        doctor.setConsultationFee(BigDecimal.valueOf(request.getConsultationFee()));

        doctorRepository.save(doctor);
        log.info("Doctor profile created for user: {}", user.getEmail());
    }

    private void createReceptionistProfile(User user, CreateUserRequest request) {
        createOrUpdateReceptionistProfile(user, request);
    }

    private void createOrUpdateReceptionistProfile(User user, CreateUserRequest request) {
        Receptionist receptionist = receptionistRepository.findByUserId(user.getId())
                .orElse(Receptionist.builder().user(user).build());
        receptionist.setEmployeeId(user.getEmployeeId());
        receptionist.setShift(request.getShift() != null ? Shift.valueOf(request.getShift()) : null);

        receptionistRepository.save(receptionist);
        log.info("Receptionist profile created for user: {}", user.getEmail());
    }

    private UserResponse mapToResponse(User user) {
        String primaryRole = user.getRoles().stream()
                .findFirst()
                .map(r -> r.getName().name())
                .orElse("NONE");

        Set<String> roleNames = user.getRoles().stream()
                .map(r -> r.getName().name())
                .collect(Collectors.toSet());

        // Compute effective page keys
        Set<String> effectivePageKeys = accessControlService.computeEffectivePageKeys(user);

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .employeeId(user.getEmployeeId())
                .phone(user.getPhone())
                .isActive(user.getIsActive())
                .role(primaryRole)
                .roles(roleNames)
                .pageKeys(new ArrayList<>(effectivePageKeys))
                .tenantId(user.getTenantId())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
