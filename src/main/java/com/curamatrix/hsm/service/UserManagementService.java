package com.curamatrix.hsm.service;

import com.curamatrix.hsm.dto.request.CreateUserRequest;
import com.curamatrix.hsm.dto.request.UpdateUserRequest;
import com.curamatrix.hsm.dto.response.UserResponse;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.RoleName;
import com.curamatrix.hsm.enums.Shift;
import com.curamatrix.hsm.repository.*;
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
    private final PasswordEncoder passwordEncoder;
    private final AccessControlService accessControlService;

    // ─── Create ──────────────────────────────────────────────────

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists: " + request.getEmail());
        }

        Role role = roleRepository.findByName(request.getRole())
                .orElseThrow(() -> new RuntimeException("Role not found: " + request.getRole()));

        Set<Role> roles = new HashSet<>();
        roles.add(role);

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .isActive(true)
                .roles(roles)
                .build();

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

    // ─── Update ──────────────────────────────────────────────────

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

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
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));

        user.getRoles().add(role);
        user = userRepository.save(user);
        log.info("Role {} added to user {}", roleName, user.getEmail());
        return mapToResponse(user);
    }

    @Transactional
    public UserResponse removeRole(Long userId, RoleName roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        boolean removed = user.getRoles().removeIf(r -> r.getName() == roleName);
        if (!removed) {
            throw new RuntimeException("User does not have role: " + roleName);
        }
        if (user.getRoles().isEmpty()) {
            throw new RuntimeException("Cannot remove the last role. A user must have at least one role.");
        }

        user = userRepository.save(user);
        log.info("Role {} removed from user {}", roleName, user.getEmail());
        return mapToResponse(user);
    }

    @Transactional
    public UserResponse setRoles(Long userId, Set<RoleName> roleNames) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        if (roleNames == null || roleNames.isEmpty()) {
            throw new RuntimeException("At least one role is required.");
        }

        Set<Role> newRoles = new HashSet<>();
        for (RoleName rn : roleNames) {
            Role role = roleRepository.findByName(rn)
                    .orElseThrow(() -> new RuntimeException("Role not found: " + rn));
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
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        user.setIsActive(false);
        userRepository.save(user);
        log.info("User deactivated: {}", user.getEmail());
    }

    @Transactional
    public void activateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        user.setIsActive(true);
        userRepository.save(user);
        log.info("User activated: {}", user.getEmail());
    }

    // ─── Delete (soft: deactivate + clear pages) ─────────────────

    @Transactional
    public void deleteUser(Long id, Long actorUserId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

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
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        return mapToResponse(user);
    }

    // ─── Audit Log ───────────────────────────────────────────────

    public List<UserAccessAudit> getAuditLog(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        return accessControlService.getAuditLog(userId, user.getTenantId());
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private void createDoctorProfile(User user, CreateUserRequest request) {
        if (request.getDepartmentId() == null || request.getSpecialization() == null ||
            request.getLicenseNumber() == null || request.getConsultationFee() == null) {
            throw new RuntimeException("Doctor profile requires: departmentId, specialization, licenseNumber, consultationFee");
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new RuntimeException("Department not found"));

        Doctor doctor = Doctor.builder()
                .user(user)
                .department(department)
                .specialization(request.getSpecialization())
                .licenseNumber(request.getLicenseNumber())
                .qualification(request.getQualification())
                .experienceYears(request.getExperienceYears())
                .consultationFee(BigDecimal.valueOf(request.getConsultationFee()))
                .build();

        doctorRepository.save(doctor);
        log.info("Doctor profile created for user: {}", user.getEmail());
    }

    private void createReceptionistProfile(User user, CreateUserRequest request) {
        Receptionist receptionist = Receptionist.builder()
                .user(user)
                .employeeId(request.getEmployeeId())
                .shift(request.getShift() != null ? Shift.valueOf(request.getShift()) : null)
                .build();

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
