package com.curamatrix.hsm.service;

import com.curamatrix.hsm.dto.request.CreateUserRequest;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists: " + request.getEmail());
        }

        // Get role
        Role role = roleRepository.findByName(request.getRole())
                .orElseThrow(() -> new RuntimeException("Role not found: " + request.getRole()));

        // Create user
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

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        return mapToResponse(user);
    }

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

    private UserResponse mapToResponse(User user) {
        String roleName = user.getRoles().stream()
                .findFirst()
                .map(r -> r.getName().name())
                .orElse("NONE");

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .isActive(user.getIsActive())
                .role(roleName)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
