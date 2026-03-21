package com.curamatrix.hsm.service;

import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.RoleName;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seeds default pages and role→page mappings on startup.
 * No permissions layer — only pages.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessBootstrapService {

    private final RoleRepository roleRepository;
    private final UiPageRepository uiPageRepository;
    private final RolePageRepository rolePageRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrapAccess() {
        seedPages();
        seedRolePages();
        log.info("Access-control bootstrap completed (simplified page-based)");
    }

    private void seedPages() {
        upsertPage("ADMIN_DASHBOARD", "/admin/dashboard", "Admin Dashboard");
        upsertPage("ADMIN_USERS", "/admin/users", "User Management");
        upsertPage("ADMIN_ROLES", "/admin/roles", "Role Management");
        upsertPage("ADMIN_PAGES", "/admin/pages", "Page Management");
        upsertPage("DOCTOR_DASHBOARD", "/doctor/dashboard", "Doctor Dashboard");
        upsertPage("DOCTOR_PRESCRIPTION", "/doctor/prescriptions", "Prescription");
        upsertPage("DOCTOR_PATIENTS", "/doctor/patients", "My Patients");
        upsertPage("RECEPTIONIST_DASHBOARD", "/receptionist/dashboard", "Receptionist Dashboard");
        upsertPage("RECEPTIONIST_APPOINTMENTS", "/receptionist/appointments", "Appointments");
        upsertPage("RECEPTIONIST_PATIENTS", "/receptionist/patients", "Patient Registration");
        upsertPage("RECEPTIONIST_BILLING", "/receptionist/billing", "Billing");
    }

    private void upsertPage(String pageKey, String route, String displayName) {
        UiPage page = uiPageRepository.findByPageKey(pageKey)
                .orElse(UiPage.builder().pageKey(pageKey).build());
        page.setRoute(route);
        page.setDisplayName(displayName);
        page.setIsActive(true);
        uiPageRepository.save(page);
    }

    private void seedRolePages() {
        Map<String, UiPage> pageMap = uiPageRepository.findAll().stream()
                .collect(Collectors.toMap(UiPage::getPageKey, p -> p));

        // Admin gets all admin pages
        assignRolePages(RoleName.ROLE_ADMIN, Set.of(
                "ADMIN_DASHBOARD", "ADMIN_USERS", "ADMIN_ROLES", "ADMIN_PAGES"
        ), pageMap);

        // Doctor gets doctor pages
        assignRolePages(RoleName.ROLE_DOCTOR, Set.of(
                "DOCTOR_DASHBOARD", "DOCTOR_PRESCRIPTION", "DOCTOR_PATIENTS"
        ), pageMap);

        // Receptionist gets receptionist pages
        assignRolePages(RoleName.ROLE_RECEPTIONIST, Set.of(
                "RECEPTIONIST_DASHBOARD", "RECEPTIONIST_APPOINTMENTS",
                "RECEPTIONIST_PATIENTS", "RECEPTIONIST_BILLING"
        ), pageMap);

        // Super Admin gets everything
        assignRolePages(RoleName.ROLE_SUPER_ADMIN, pageMap.keySet(), pageMap);
    }

    private void assignRolePages(RoleName roleName, Set<String> pageKeys, Map<String, UiPage> pageMap) {
        Role role = roleRepository.findByName(roleName).orElse(null);
        if (role == null) return;

        List<String> existingKeys = rolePageRepository.findActivePageKeysByRoleId(role.getId());
        Set<String> existingSet = Set.copyOf(existingKeys);

        for (String key : pageKeys) {
            if (!existingSet.contains(key) && pageMap.containsKey(key)) {
                rolePageRepository.save(RolePage.builder()
                        .role(role)
                        .page(pageMap.get(key))
                        .build());
            }
        }
    }
}
