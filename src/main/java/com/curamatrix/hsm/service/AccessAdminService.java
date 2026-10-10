package com.curamatrix.hsm.service;

import com.curamatrix.hsm.dto.access.PageResponse;
import com.curamatrix.hsm.dto.access.PageUpsertRequest;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.RoleName;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin service for managing pages and role-page mappings.
 * No permissions layer — everything is about pages.
 */
@Service
@RequiredArgsConstructor
public class AccessAdminService {

    private final RoleRepository roleRepository;
    private final UiPageRepository uiPageRepository;
    private final RolePageRepository rolePageRepository;

    // ─── Page CRUD ──────────────────────────────────────────────

    public List<PageResponse> getAllPages() {
        return uiPageRepository.findAll().stream()
                .map(page -> PageResponse.builder()
                        .pageKey(page.getPageKey())
                        .route(page.getRoute())
                        .displayName(page.getDisplayName())
                        .active(page.getIsActive())
                        .build())
                .sorted(Comparator.comparing(PageResponse::getPageKey))
                .toList();
    }

    @Transactional
    public UiPage upsertPage(PageUpsertRequest request) {
        UiPage page = uiPageRepository.findByPageKey(request.getPageKey())
                .orElse(UiPage.builder().pageKey(request.getPageKey()).build());
        page.setRoute(request.getRoute());
        page.setDisplayName(request.getDisplayName());
        page.setIsActive(true);
        return uiPageRepository.save(page);
    }

    @Transactional
    public void softDeletePage(String pageKey) {
        UiPage page = uiPageRepository.findByPageKey(pageKey)
                .orElseThrow(() -> new RuntimeException("Page not found: " + pageKey));
        page.setIsActive(false);
        uiPageRepository.save(page);
    }

    // ─── Role → Pages mapping ───────────────────────────────────

    /**
     * Get page keys assigned to a role.
     */
    public Set<String> getRolePageKeys(RoleName roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));
        return new LinkedHashSet<>(rolePageRepository.findActivePageKeysByRoleId(role.getId()));
    }

    /**
     * Replace all page assignments for a role.
     */
    @Transactional
    public void setRolePages(RoleName roleName, Set<String> pageKeys) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));

        Set<String> targetKeys = (pageKeys == null) ? Collections.emptySet() : pageKeys;

        if (!targetKeys.isEmpty()) {
            List<UiPage> pages = uiPageRepository.findByPageKeyIn(targetKeys);
            if (pages.size() != targetKeys.size()) {
                Set<String> found = pages.stream().map(UiPage::getPageKey).collect(Collectors.toSet());
                Set<String> missing = new HashSet<>(targetKeys);
                missing.removeAll(found);
                throw new RuntimeException("Unknown pages: " + missing);
            }
        }

        List<RolePage> currentRolePages = rolePageRepository.findByRoleId(role.getId());

        // Remove role pages that are no longer in targetKeys
        List<RolePage> toRemove = currentRolePages.stream()
                .filter(rp -> !targetKeys.contains(rp.getPage().getPageKey()))
                .toList();

        if (!toRemove.isEmpty()) {
            rolePageRepository.deleteAll(toRemove);
            rolePageRepository.flush();
        }

        // Add role pages that are in targetKeys but not in currentRolePages
        Set<String> currentKeys = currentRolePages.stream()
                .map(rp -> rp.getPage().getPageKey())
                .collect(Collectors.toSet());

        Set<String> toAddKeys = targetKeys.stream()
                .filter(k -> !currentKeys.contains(k))
                .collect(Collectors.toSet());

        if (!toAddKeys.isEmpty()) {
            List<UiPage> pagesToAdd = uiPageRepository.findByPageKeyIn(toAddKeys);
            List<RolePage> toAdd = pagesToAdd.stream()
                    .map(page -> RolePage.builder().role(role).page(page).build())
                    .toList();
            rolePageRepository.saveAll(toAdd);
        }
    }
}
