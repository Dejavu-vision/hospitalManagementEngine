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

        rolePageRepository.deleteByRoleId(role.getId());

        if (pageKeys != null && !pageKeys.isEmpty()) {
            List<UiPage> pages = uiPageRepository.findByPageKeyIn(pageKeys);
            if (pages.size() != pageKeys.size()) {
                Set<String> found = pages.stream().map(UiPage::getPageKey).collect(Collectors.toSet());
                Set<String> missing = new HashSet<>(pageKeys);
                missing.removeAll(found);
                throw new RuntimeException("Unknown pages: " + missing);
            }
            for (UiPage page : pages) {
                rolePageRepository.save(RolePage.builder().role(role).page(page).build());
            }
        }
    }
}
