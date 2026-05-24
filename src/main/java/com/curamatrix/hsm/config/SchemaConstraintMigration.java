package com.curamatrix.hsm.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time schema migration: drops legacy unique constraints that were replaced
 * by per-doctor-scoped constraints in the per-doctor token sequence fix.
 *
 * Safe to run repeatedly — each DROP is guarded by an existence check.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SchemaConstraintMigration implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        // Log all current constraints on walk_in_token_sequence so we know exact names
        logConstraints("walk_in_token_sequence");
        logConstraints("blocked_tokens");

        // Drop all non-PRIMARY unique constraints on walk_in_token_sequence
        // then let Hibernate recreate the correct (date, tenant_id, doctor_id) one
        dropAllUniqueConstraints("walk_in_token_sequence");
        dropAllUniqueConstraints("blocked_tokens");

        // Clean up legacy PENDING_ACCEPTANCE appointments to CHECKED_IN
        cleanupPendingAcceptanceAppointments();
    }

    private void cleanupPendingAcceptanceAppointments() {
        try {
            int updatedAppts = jdbc.update(
                "UPDATE appointments SET status = 'CHECKED_IN' WHERE status = 'PENDING_ACCEPTANCE'"
            );
            log.info("SchemaConstraintMigration: successfully cleaned up {} legacy appointments from PENDING_ACCEPTANCE to CHECKED_IN", updatedAppts);
        } catch (Exception e) {
            log.warn("SchemaConstraintMigration: could not clean up PENDING_ACCEPTANCE appointments: {}", e.getMessage());
        }

        try {
            int updatedNewLogs = jdbc.update(
                "UPDATE appointment_status_log SET new_status = 'CHECKED_IN' WHERE new_status = 'PENDING_ACCEPTANCE'"
            );
            int updatedPrevLogs = jdbc.update(
                "UPDATE appointment_status_log SET previous_status = 'CHECKED_IN' WHERE previous_status = 'PENDING_ACCEPTANCE'"
            );
            log.info("SchemaConstraintMigration: successfully cleaned up {}/{} legacy appointment status logs from PENDING_ACCEPTANCE to CHECKED_IN", 
                updatedNewLogs, updatedPrevLogs);
        } catch (Exception e) {
            log.warn("SchemaConstraintMigration: could not clean up PENDING_ACCEPTANCE logs: {}", e.getMessage());
        }
    }

    private void logConstraints(String table) {
        try {
            jdbc.query(
                "SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                (rs) -> {
                    log.info("SchemaConstraintMigration: table='{}' constraint='{}' type='{}'",
                            table, rs.getString("CONSTRAINT_NAME"), rs.getString("CONSTRAINT_TYPE"));
                }, table);
        } catch (Exception e) {
            log.warn("SchemaConstraintMigration: could not list constraints for '{}': {}", table, e.getMessage());
        }
    }

    private void dropAllUniqueConstraints(String table) {
        try {
            // Find all UNIQUE constraints (not PRIMARY KEY) on this table
            var constraints = jdbc.queryForList(
                "SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_TYPE = 'UNIQUE'",
                String.class, table);

            for (String name : constraints) {
                try {
                    jdbc.execute("ALTER TABLE `" + table + "` DROP INDEX `" + name + "`");
                    log.info("SchemaConstraintMigration: dropped UNIQUE index '{}' on '{}'", name, table);
                } catch (Exception e) {
                    log.warn("SchemaConstraintMigration: could not drop '{}' on '{}': {}", name, table, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("SchemaConstraintMigration: error processing table '{}': {}", table, e.getMessage());
        }
    }
}
