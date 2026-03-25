package com.curamatrix.hsm.service;

import com.curamatrix.hsm.dto.access.PageAccessDto;
import com.curamatrix.hsm.dto.access.UserAccessResponse;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.entity.UserPage.Effect;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Access control — everything is about PAGES.
 *
 * Effective pages = (role default pages − DENY overrides) + GRANT overrides.
 *
 * Super Admin has full control: can add any page (GRANT) or remove any page
 * including role-defaults (DENY). No restrictions.
 */
@Service
@RequiredArgsConstructor
public class AccessControlService {

        private final RolePageRepository rolePageRepository;
        private final UserPageRepository userPageRepository;
        private final UiPageRepository uiPageRepository;
        private final UserRepository userRepository;
        private final UserAccessAuditRepository userAccessAuditRepository;

        // ─── Helpers ────────────────────────────────────────────────

        /** Collect all role-default page keys for a user. */
        public Set<String> getRolePageKeys(User user) {
                Set<Long> roleIds = user.getRoles().stream().map(Role::getId).collect(Collectors.toSet());
                Set<String> rolePages = new LinkedHashSet<>();
                for (Long roleId : roleIds) {
                        rolePages.addAll(rolePageRepository.findActivePageKeysByRoleId(roleId));
                }
                return rolePages;
        }

        // ─── Effective pages ────────────────────────────────────────

        /**
         * Compute all page keys this user can actually access.
         * = (role default pages − DENY overrides) + GRANT overrides
         */
        public Set<String> computeEffectivePageKeys(User user) {
                Set<String> rolePages = getRolePageKeys(user);

                // User-level overrides
                Set<String> granted = new LinkedHashSet<>(
                                userPageRepository.findGrantedPageKeysByUserIdAndTenantId(user.getId(),
                                                user.getTenantId()));
                Set<String> denied = new LinkedHashSet<>(
                                userPageRepository.findDeniedPageKeysByUserIdAndTenantId(user.getId(),
                                                user.getTenantId()));

                // effective = (role − denied) + granted
                Set<String> effective = new LinkedHashSet<>(rolePages);
                effective.removeAll(denied);
                effective.addAll(granted);
                return effective;
        }

        /**
         * Build full access response for a user (used by admin lookup +
         * /api/me/access).
         */
        public UserAccessResponse getUserAccess(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

                Set<String> roleNames = user.getRoles().stream()
                                .map(r -> r.getName().name())
                                .collect(Collectors.toSet());

                Set<String> effectivePageKeys = computeEffectivePageKeys(user);

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

        // ─── Bulk set (replaces all overrides) ─────────────────────

        /**
         * Set extra GRANT pages for a user (replaces all existing overrides — both
         * GRANT and DENY).
         */
        @Transactional
        public void setUserPages(Long userId, Long tenantId, Set<String> pageKeys, Long changedByUserId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
                Long tid = user.getTenantId();

                userPageRepository.deleteByUserIdAndTenantId(userId, tid);

                if (pageKeys != null && !pageKeys.isEmpty()) {
                        List<UiPage> pages = uiPageRepository.findByPageKeyIn(pageKeys);
                        for (UiPage page : pages) {
                                UserPage up = UserPage.builder()
                                                .user(user).page(page).effect(Effect.GRANT).build();
                                up.setTenantId(tid);
                                userPageRepository.save(up);
                        }
                }
                audit(tid, userId, changedByUserId, "USER_PAGE_SET",
                                "pages=" + (pageKeys == null ? "[]" : pageKeys));
        }

        // ─── Single page add (GRANT) ───────────────────────────────

        /**
         * Grant a single page to a user.
         * If a DENY record exists for this page, it is flipped to GRANT.
         * If a GRANT record already exists, no-op.
         */
        @Transactional
        public void addUserPage(Long userId, String pageKey, Long changedByUserId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
                Long tid = user.getTenantId();

                Optional<UserPage> existing = userPageRepository
                                .findByUserIdAndPageKeyAndTenantId(userId, pageKey, tid);

                if (existing.isPresent()) {
                        UserPage up = existing.get();
                        if (up.getEffect() == Effect.GRANT) {
                                return; // already granted
                        }
                        // Was DENY → flip to GRANT
                        up.setEffect(Effect.GRANT);
                        userPageRepository.save(up);
                        audit(tid, userId, changedByUserId, "USER_PAGE_ADD",
                                        "Flipped DENY→GRANT: " + pageKey);
                        return;
                }

                // No record exists — check if it's already a role-default page
                Set<String> rolePages = getRolePageKeys(user);
                if (rolePages.contains(pageKey)) {
                        // Already accessible via role, and no DENY exists → nothing to do
                        return;
                }

                UiPage page = uiPageRepository.findByPageKey(pageKey)
                                .orElseThrow(() -> new ResourceNotFoundException("Page", "pageKey", pageKey));

                UserPage up = UserPage.builder()
                                .user(user).page(page).effect(Effect.GRANT).build();
                up.setTenantId(tid);
                userPageRepository.save(up);

                audit(tid, userId, changedByUserId, "USER_PAGE_ADD", "Added page: " + pageKey);
        }

        // ─── Single page remove (DENY or delete GRANT) ─────────────

        /**
         * Remove a page from a user's effective access.
         * - If the page was an extra GRANT → delete the record.
         * - If the page comes from the user's role → create a DENY record.
         */
        @Transactional
        public void removeUserPage(Long userId, String pageKey, Long changedByUserId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
                Long tid = user.getTenantId();

                Optional<UserPage> existing = userPageRepository
                                .findByUserIdAndPageKeyAndTenantId(userId, pageKey, tid);

                if (existing.isPresent()) {
                        UserPage up = existing.get();
                        if (up.getEffect() == Effect.GRANT) {
                                // Extra page → just delete
                                userPageRepository.delete(up);
                                audit(tid, userId, changedByUserId, "USER_PAGE_REMOVE",
                                                "Removed extra page: " + pageKey);
                                return;
                        }
                        // Already DENY → nothing to do
                        return;
                }

                // No user-page record — check if it's a role-default page
                Set<String> rolePages = getRolePageKeys(user);
                if (!rolePages.contains(pageKey)) {
                        throw new ResourceNotFoundException("Page '" + pageKey + "' is not in user's access");
                }

                // Role-default page → create DENY record
                UiPage page = uiPageRepository.findByPageKey(pageKey)
                                .orElseThrow(() -> new ResourceNotFoundException("Page", "pageKey", pageKey));

                UserPage deny = UserPage.builder()
                                .user(user).page(page).effect(Effect.DENY).build();
                deny.setTenantId(tid);
                userPageRepository.save(deny);

                audit(tid, userId, changedByUserId, "USER_PAGE_DENY",
                                "Denied role-default page: " + pageKey);
        }

        // ─── Query helpers ──────────────────────────────────────────

        /** Get only the extra GRANT page keys for a user. */
        public Set<String> getUserExtraPageKeys(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
                return new LinkedHashSet<>(
                                userPageRepository.findGrantedPageKeysByUserIdAndTenantId(
                                                user.getId(), user.getTenantId()));
        }

        /** Get only the DENY page keys for a user. */
        public Set<String> getUserDeniedPageKeys(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
                return new LinkedHashSet<>(
                                userPageRepository.findDeniedPageKeysByUserIdAndTenantId(
                                                user.getId(), user.getTenantId()));
        }

        // ─── Restore denied page ────────────────────────────────────

        /**
         * Restore a page that was explicitly denied.
         * Simply removes the DENY record so the role-default kicks back in.
         * If no DENY record exists, this is a no-op.
         */
        @Transactional
        public void restoreDeniedPage(Long userId, String pageKey, Long changedByUserId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
                Long tid = user.getTenantId();

                Optional<UserPage> existing = userPageRepository
                                .findByUserIdAndPageKeyAndTenantId(userId, pageKey, tid);

                if (existing.isPresent() && existing.get().getEffect() == Effect.DENY) {
                        userPageRepository.delete(existing.get());
                        audit(tid, userId, changedByUserId, "USER_PAGE_RESTORE",
                                        "Restored denied page: " + pageKey);
                }
        }

        // ─── Reset ─────────────────────────────────────────────────

        /**
         * Reset all user-specific overrides (both GRANT and DENY).
         * Reverts to pure role defaults.
         */
        @Transactional
        public void resetUserPages(Long userId, Long tenantId, Long changedByUserId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
                Long tid = user.getTenantId();
                userPageRepository.deleteByUserIdAndTenantId(userId, tid);
                audit(tid, userId, changedByUserId, "USER_PAGE_RESET", "Reverted to role defaults");
        }

        // ─── Audit ─────────────────────────────────────────────────

        public List<UserAccessAudit> getAuditLog(Long userId, Long tenantId) {
                return userAccessAuditRepository.findByTargetUserIdAndTenantIdOrderByCreatedAtDesc(userId, tenantId);
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
