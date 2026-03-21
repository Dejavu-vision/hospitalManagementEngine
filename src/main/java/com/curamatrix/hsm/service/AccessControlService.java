package com.curamatrix.hsm.service;

import com.curamatrix.hsm.dto.access.PageAccessDto;
import com.curamatrix.hsm.dto.access.UserAccessResponse;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simplified access control — everything is about PAGES.
 *
 * Effective pages for a user = (pages from their role) + (extra pages assigned directly to the user).
 *
 * No permissions layer. No ALLOW/DENY. Just direct page assignment.
 */
@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final RolePageRepository rolePageRepository;
    private final UserPageRepository userPageRepository;
    private final UiPageRepository uiPageRepository;
    private final UserRepository userRepository;
    private final UserAccessAuditRepository userAccessAuditRepository;

    /**
     * Compute all page keys this user can access.
     * = role default pages + user-specific extra pages
     */
    public Set<String> computeEffectivePageKeys(User user) {
        // 1. Collect page keys from all roles
        Set<Long> roleIds = user.getRoles().stream().map(Role::getId).collect(Collectors.toSet());
        Set<String> rolePages = new LinkedHashSet<>();
        for (Long roleId : roleIds) {
            rolePages.addAll(rolePageRepository.findActivePageKeysByRoleId(roleId));
        }

        // 2. Collect extra page keys assigned directly to the user
        List<String> userExtraPages = userPageRepository
                .findActivePageKeysByUserIdAndTenantId(user.getId(), user.getTenantId());

        // 3. Merge
        Set<String> effective = new LinkedHashSet<>(rolePages);
        effective.addAll(userExtraPages);
        return effective;
    }

    /**
     * Build full access response for a user (used by admin lookup + /api/me/access).
     */
    public UserAccessResponse getUserAccess(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Set<String> roleNames = user.getRoles().stream()
                .map(r -> r.getName().name())
                .collect(Collectors.toSet());

        Set<String> effectivePageKeys = computeEffectivePageKeys(user);

        // Resolve full page info for the effective keys
        List<UiPage> allActivePages = uiPageRepository.findByIsActiveTrue();
        List<PageAccessDto> pages = allActivePages.stream()
                .filter(p -> effectivePageKeys.contains(p.getPageKey()))
                .map(p -> PageAccessDto.builder()
                        .pageKey(p.getPageKey())
                        .route(p.getRoute())
                        .displayName(p.getDisplayName())
                        .build())
                .sorted(Comparator.comparing(PageAccessDto::getPageKey))
                .collect(Collectors.toList());

        return UserAccessResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .roles(roleNames)
                .pages(pages)
                .build();
    }

    /**
     * Set extra pages for a user (replaces any existing user-page overrides).
     */
    @Transactional
    public void setUserPages(Long userId, Long tenantId, Set<String> pageKeys, Long changedByUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // Clear existing user-page assignments
        userPageRepository.deleteByUserIdAndTenantId(userId, tenantId);

        // Assign new pages
        if (pageKeys != null && !pageKeys.isEmpty()) {
            List<UiPage> pages = uiPageRepository.findByPageKeyIn(pageKeys);
            for (UiPage page : pages) {
                UserPage userPage = UserPage.builder()
                        .user(user)
                        .page(page)
                        .build();
                userPage.setTenantId(tenantId);
                userPageRepository.save(userPage);
            }
        }

        audit(tenantId, userId, changedByUserId, "USER_PAGE_SET",
                "pages=" + (pageKeys == null ? "[]" : pageKeys));
    }

    /**
     * Reset user-specific page overrides (revert to role defaults only).
     */
    @Transactional
    public void resetUserPages(Long userId, Long tenantId, Long changedByUserId) {
        userPageRepository.deleteByUserIdAndTenantId(userId, tenantId);
        audit(tenantId, userId, changedByUserId, "USER_PAGE_RESET", "Reverted to role defaults");
    }

    private void audit(Long tenantId, Long targetUserId, Long changedByUserId, String eventType, String details) {
        UserAccessAudit record = UserAccessAudit.builder()
                .targetUserId(targetUserId)
                .changedByUserId(changedByUserId)
                .eventType(eventType)
                .details(details)
                .build();
        record.setTenantId(tenantId);
        userAccessAuditRepository.save(record);
    }
}
