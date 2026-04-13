-- Lab Services Module — UI Page and Role-Page Seed Data
-- Run this script against the MySQL database after deploying the lab services module.
-- It registers the lab UI pages and maps them to the appropriate roles.

-- 1. Insert UI Pages for Lab module
INSERT INTO ui_pages (page_key, route, display_name, is_active)
VALUES ('LAB_DASHBOARD', '/lab-dashboard', 'Lab Dashboard', true)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), is_active = VALUES(is_active);

INSERT INTO ui_pages (page_key, route, display_name, is_active)
VALUES ('LAB_SERVICES', '/lab-services', 'Lab Services', true)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), is_active = VALUES(is_active);

INSERT INTO ui_pages (page_key, route, display_name, is_active)
VALUES ('LAB_REGISTRATION', '/lab-registration', 'Lab Registration', true)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), is_active = VALUES(is_active);

-- 2. Insert Role for ROLE_LAB_STAFF (if not already present)
INSERT IGNORE INTO roles (name) VALUES ('ROLE_LAB_STAFF');

-- 3. Map roles to lab pages
-- ROLE_LAB_STAFF → Lab Dashboard
INSERT IGNORE INTO role_pages (role_id, page_id)
SELECT r.id, p.id FROM roles r, ui_pages p
WHERE r.name = 'ROLE_LAB_STAFF' AND p.page_key = 'LAB_DASHBOARD';

-- ROLE_ADMIN → Lab Services
INSERT IGNORE INTO role_pages (role_id, page_id)
SELECT r.id, p.id FROM roles r, ui_pages p
WHERE r.name = 'ROLE_ADMIN' AND p.page_key = 'LAB_SERVICES';

-- ROLE_RECEPTIONIST → Lab Registration
INSERT IGNORE INTO role_pages (role_id, page_id)
SELECT r.id, p.id FROM roles r, ui_pages p
WHERE r.name = 'ROLE_RECEPTIONIST' AND p.page_key = 'LAB_REGISTRATION';
