package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.access.*;
import com.curamatrix.hsm.entity.User;
import com.curamatrix.hsm.enums.RoleName;
import com.curamatrix.hsm.repository.UserRepository;
import com.curamatrix.hsm.service.AccessAdminService;
import com.curamatrix.hsm.service.AccessControlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * Simplified access admin controller.
 * Manages: Pages (CRUD), Role→Pages mapping, User→Pages overrides.
 */
@RestController
@RequestMapping("/api/admin/access")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AdminAccessController {

    private final AccessControlService accessControlService;
    private final AccessAdminService accessAdminService;
    private final UserRepository userRepository;

    // ─── User access lookup ─────────────────────────────────────

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserAccessResponse> getUserAccess(@PathVariable Long userId) {
        return ResponseEntity.ok(accessControlService.getUserAccess(userId));
    }

    // ─── User page overrides ────────────────────────────────────

    @PutMapping("/users/{userId}/pages")
    public ResponseEntity<Void> setUserPages(@PathVariable Long userId,
                                             @RequestBody Set<String> pageKeys,
                                             Authentication authentication) {
        User actor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        accessControlService.setUserPages(userId, TenantContext.getTenantId(), pageKeys, actor.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{userId}/pages")
    public ResponseEntity<Void> resetUserPages(@PathVariable Long userId,
                                               Authentication authentication) {
        User actor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        accessControlService.resetUserPages(userId, TenantContext.getTenantId(), actor.getId());
        return ResponseEntity.noContent().build();
    }

    // ─── Role → Pages mapping ───────────────────────────────────

    @GetMapping("/roles/{roleName}/pages")
    public ResponseEntity<Set<String>> getRolePages(@PathVariable RoleName roleName) {
        return ResponseEntity.ok(accessAdminService.getRolePageKeys(roleName));
    }

    @PutMapping("/roles/{roleName}/pages")
    public ResponseEntity<Void> setRolePages(@PathVariable RoleName roleName,
                                             @RequestBody Set<String> pageKeys) {
        accessAdminService.setRolePages(roleName, pageKeys);
        return ResponseEntity.noContent().build();
    }

    // ─── Page CRUD ──────────────────────────────────────────────

    @GetMapping("/pages")
    public ResponseEntity<List<PageResponse>> getAllPages() {
        return ResponseEntity.ok(accessAdminService.getAllPages());
    }

    @PostMapping("/pages")
    public ResponseEntity<Void> upsertPage(@Valid @RequestBody PageUpsertRequest request) {
        accessAdminService.upsertPage(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/pages/{pageKey}")
    public ResponseEntity<Void> softDeletePage(@PathVariable String pageKey) {
        accessAdminService.softDeletePage(pageKey);
        return ResponseEntity.noContent().build();
    }
}
