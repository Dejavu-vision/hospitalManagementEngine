package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.CreateUserRequest;
import com.curamatrix.hsm.dto.request.UpdateUserRequest;
import com.curamatrix.hsm.dto.response.UserResponse;
import com.curamatrix.hsm.entity.User;
import com.curamatrix.hsm.entity.UserAccessAudit;
import com.curamatrix.hsm.enums.RoleName;
import com.curamatrix.hsm.repository.UserRepository;
import com.curamatrix.hsm.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@io.swagger.v3.oas.annotations.tags.Tag(name = "2. Admin - User Management", description = "User creation and management (Admin only)")
public class AdminController {

    private final UserManagementService userManagementService;
    private final UserRepository userRepository;

    // ─── CRUD ────────────────────────────────────────────────────

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request,
            @RequestParam(required = false) Long tenantId,
            Authentication authentication) {
        log.info("Creating new user: {}", request.getEmail());
        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));

        Long effectiveTenantId;
        if (isSuperAdmin) {
            if (tenantId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "tenantId is required when creating users as SUPER_ADMIN");
            }
            effectiveTenantId = tenantId;
        } else {
            User currentUser = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
            effectiveTenantId = currentUser.getTenantId();
            if (tenantId != null && !tenantId.equals(effectiveTenantId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You cannot create users for another tenant");
            }
        }

        UserResponse response = userManagementService.createUser(request, effectiveTenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers(
            @RequestParam(required = false) Long tenantId,
            Authentication authentication) {
        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));

        List<UserResponse> users;
        if (isSuperAdmin) {
            users = (tenantId != null)
                    ? userManagementService.getUsersByTenantId(tenantId)
                    : userManagementService.getAllUsers();
        } else {
            User currentUser = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
            users = userManagementService.getUsersByTenantId(currentUser.getTenantId());
        }
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse user = userManagementService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        log.info("Updating user: {}", id);
        UserResponse response = userManagementService.updateUser(id, request);
        return ResponseEntity.ok(response);
    }

    // ─── Activate / Deactivate ───────────────────────────────────

    @PutMapping("/users/{id}/deactivate")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        log.info("Deactivating user: {}", id);
        userManagementService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/activate")
    public ResponseEntity<Void> activateUser(@PathVariable Long id) {
        log.info("Activating user: {}", id);
        userManagementService.activateUser(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Delete (soft) ───────────────────────────────────────────

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, Authentication authentication) {
        log.info("Deleting user: {}", id);
        User actor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        userManagementService.deleteUser(id, actor.getId());
        return ResponseEntity.noContent().build();
    }

    // ─── Role Management ─────────────────────────────────────────

    @PutMapping("/users/{id}/roles")
    public ResponseEntity<UserResponse> setRoles(@PathVariable Long id,
            @RequestBody Set<RoleName> roleNames) {
        log.info("Setting roles for user {}: {}", id, roleNames);
        UserResponse response = userManagementService.setRoles(id, roleNames);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{id}/roles/{roleName}")
    public ResponseEntity<UserResponse> addRole(@PathVariable Long id,
            @PathVariable RoleName roleName) {
        log.info("Adding role {} to user {}", roleName, id);
        UserResponse response = userManagementService.addRole(id, roleName);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/users/{id}/roles/{roleName}")
    public ResponseEntity<UserResponse> removeRole(@PathVariable Long id,
            @PathVariable RoleName roleName) {
        log.info("Removing role {} from user {}", roleName, id);
        UserResponse response = userManagementService.removeRole(id, roleName);
        return ResponseEntity.ok(response);
    }

    // ─── Audit Log ───────────────────────────────────────────────

    @GetMapping("/users/{id}/audit")
    public ResponseEntity<List<UserAccessAudit>> getAuditLog(@PathVariable Long id) {
        List<UserAccessAudit> auditLog = userManagementService.getAuditLog(id);
        return ResponseEntity.ok(auditLog);
    }
}
