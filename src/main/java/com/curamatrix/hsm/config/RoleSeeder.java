package com.curamatrix.hsm.config;

import com.curamatrix.hsm.entity.Role;
import com.curamatrix.hsm.enums.RoleName;
import com.curamatrix.hsm.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the fixed set of application roles ({@link RoleName}) on startup.
 *
 * Runs with {@code @Order(0)} so roles exist before any other seeder/bootstrap
 * (e.g. AccessBootstrapService role→page mapping) needs them.
 *
 * Idempotent: only inserts roles that are missing.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class RoleSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;
        for (RoleName name : RoleName.values()) {
            if (roleRepository.findByName(name).isEmpty()) {
                roleRepository.save(Role.builder().name(name).build());
                created++;
            }
        }
        if (created > 0) {
            log.info("RoleSeeder: created {} missing role(s).", created);
        } else {
            log.info("RoleSeeder: all roles already present — nothing to seed.");
        }
    }
}
