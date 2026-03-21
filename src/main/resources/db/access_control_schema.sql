-- ============================================================
-- SIMPLIFIED ACCESS CONTROL SCHEMA
-- Architecture: Role → Pages + User → Extra Pages
-- No permissions layer. Direct page assignment.
-- ============================================================

-- Drop old permission-based tables
DROP TABLE IF EXISTS page_permissions;
DROP TABLE IF EXISTS role_permissions;
DROP TABLE IF EXISTS user_permissions;
DROP TABLE IF EXISTS permissions;

-- UI Pages (the only "access unit")
CREATE TABLE IF NOT EXISTS ui_pages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    page_key VARCHAR(100) NOT NULL,
    route VARCHAR(200) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_ui_page_key UNIQUE (page_key),
    CONSTRAINT uk_ui_page_route UNIQUE (route)
);

-- Role → Pages (default pages for each role)
CREATE TABLE IF NOT EXISTS role_pages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    page_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_page FOREIGN KEY (page_id) REFERENCES ui_pages (id) ON DELETE CASCADE,
    CONSTRAINT uk_role_page UNIQUE (role_id, page_id)
);

-- User → Extra Pages (admin assigns extra pages to a specific user)
CREATE TABLE IF NOT EXISTS user_pages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    page_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_up_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_up_page FOREIGN KEY (page_id) REFERENCES ui_pages (id) ON DELETE CASCADE,
    CONSTRAINT fk_up_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT uk_user_page UNIQUE (user_id, page_id)
);

-- Audit log
CREATE TABLE IF NOT EXISTS user_access_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    changed_by_user_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    details VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_audit_target FOREIGN KEY (target_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_audit_actor FOREIGN KEY (changed_by_user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Seed pages
INSERT IGNORE INTO ui_pages (page_key, route, display_name) VALUES
('ADMIN_DASHBOARD',            '/admin/dashboard',             'Admin Dashboard'),
('ADMIN_USERS',                '/admin/users',                 'User Management'),
('ADMIN_ROLES',                '/admin/roles',                 'Role Management'),
('ADMIN_PAGES',                '/admin/pages',                 'Page Management'),
('DOCTOR_DASHBOARD',           '/doctor/dashboard',            'Doctor Dashboard'),
('DOCTOR_PRESCRIPTION',        '/doctor/prescriptions',        'Prescription'),
('DOCTOR_APPOINTMENTS',        '/doctor/appointments',         'Doctor Appointments'),
('RECEPTIONIST_DASHBOARD',     '/receptionist/dashboard',      'Receptionist Dashboard'),
('RECEPTIONIST_APPOINTMENTS',  '/receptionist/appointments',   'Appointments'),
('RECEPTIONIST_PATIENTS',      '/receptionist/patients',       'Patient Registration'),
('MEDICINE_SEARCH',            '/medicines/search',            'Medicine Search'),
('BILLING',                    '/billing',                     'Billing & Invoices'),
('REPORTS',                    '/reports',                     'Reports & Analytics');

