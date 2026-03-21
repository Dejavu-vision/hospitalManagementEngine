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

    // ─── Single page add / remove ───────────────────────────────

    @PostMapping("/users/{userId}/pages/{pageKey}")
    public ResponseEntity<Void> addSingleUserPage(@PathVariable Long userId,
                                                  @PathVariable String pageKey,
                                                  Authentication authentication) {
        User actor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        accessControlService.addUserPage(userId, pageKey, actor.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{userId}/pages/{pageKey}")
    public ResponseEntity<Void> removeSingleUserPage(@PathVariable Long userId,
                                                     @PathVariable String pageKey,
                                                     Authentication authentication) {
        User actor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        accessControlService.removeUserPage(userId, pageKey, actor.getId());
        return ResponseEntity.noContent().build();
    }

    /** Page keys explicitly GRANTED (extra pages beyond role defaults). */
    @GetMapping("/users/{userId}/pages/extra")
    public ResponseEntity<Set<String>> getUserExtraPages(@PathVariable Long userId) {
        return ResponseEntity.ok(accessControlService.getUserExtraPageKeys(userId));
    }

    /** Alias for /extra — same data, clearer name. */
    @GetMapping("/users/{userId}/pages/granted")
    public ResponseEntity<Set<String>> getUserGrantedPages(@PathVariable Long userId) {
        return ResponseEntity.ok(accessControlService.getUserExtraPageKeys(userId));
    }

    /** Page keys explicitly DENIED (blocked from role defaults) for this user. */
    @GetMapping("/users/{userId}/pages/denied")
    public ResponseEntity<Set<String>> getUserDeniedPages(@PathVariable Long userId) {
        return ResponseEntity.ok(accessControlService.getUserDeniedPageKeys(userId));
    }

    // ─── Explicit deny / restore endpoints ───────────────────────

    /**
     * Deny a specific page for a user (blocks a role-default page).
     * Equivalent to DELETE /users/{userId}/pages/{pageKey} when the page is role-default,
     * but provided as a dedicated endpoint for clarity.
     */
    @PostMapping("/users/{userId}/pages/{pageKey}/deny")
    public ResponseEntity<Void> denyUserPage(@PathVariable Long userId,
                                             @PathVariable String pageKey,
                                             Authentication authentication) {
        User actor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        accessControlService.removeUserPage(userId, pageKey, actor.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Restore a denied page (removes the DENY record, re-enabling role-default access).
     */
    @DeleteMapping("/users/{userId}/pages/{pageKey}/deny")
    public ResponseEntity<Void> restoreDeniedPage(@PathVariable Long userId,
                                                  @PathVariable String pageKey,
                                                  Authentication authentication) {
        User actor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        accessControlService.restoreDeniedPage(userId, pageKey, actor.getId());
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
