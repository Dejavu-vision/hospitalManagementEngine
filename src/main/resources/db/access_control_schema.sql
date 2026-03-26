-- ============================================================
-- HMS ACCESS CONTROL SCHEMA - ACTUAL VALUES
-- ============================================================

-- 1. Ensure Roles exist
INSERT IGNORE INTO roles (name) VALUES 
('ROLE_SUPER_ADMIN'), 
('ROLE_ADMIN'), 
('ROLE_DOCTOR'), 
('ROLE_RECEPTIONIST');

-- 2. Clear old mappings to avoid duplicates during re-seed
DELETE FROM role_pages;
DELETE FROM ui_pages;

-- 3. Seed ALL UI Pages
INSERT INTO ui_pages (page_key, route, display_name) VALUES
-- Shared / Core
('ADMIN_DASHBOARD',        '/dashboard',     'Admin Dashboard'),
('RECEPTIONIST_DASHBOARD', '/dashboard',     'Receptionist Dashboard'),
('DOCTOR_DASHBOARD',       '/dashboard',     'Doctor Dashboard'),

-- Main Modules
('PATIENTS',               '/patients',      'Patient Management'),
('DOCTORS',                '/doctors',       'Doctor Directory'),
('APPOINTMENTS',           '/appointments',  'Appointments'),
('PHARMACY',               '/pharmacy',      'Pharmacy'),
('BILLING',                '/billing',       'Billing'),
('REPORTS',                '/reports',       'Reports'),

-- Admin Specific
('ADMIN_USERS',            '/access',        'Staff Management'),
('ADMIN_ROLES',            '/roles',         'Role Management'),
('ADMIN_PAGES',            '/pages',         'Page Config'),
('ADMIN_SETTINGS',         '/settings',      'System Settings'),

-- Super Admin
('SUPER_ADMIN',            '/superadmin',    'Hospital Management'),

-- Clinical / Specific
('DOCTOR_PRESCRIPTION',    '/consultation',  'Prescription'),
('DOCTOR_PATIENTS',        '/patients',      'My Patients'),
('RECEPTIONIST_PATIENTS',  '/patients',      'Patient Intake');

-- 4. Map Pages to Roles (ROLE_ADMIN)
INSERT INTO role_pages (role_id, page_id)
SELECT r.id, p.id FROM roles r, ui_pages p 
WHERE r.name = 'ROLE_ADMIN' 
AND p.page_key IN ('ADMIN_DASHBOARD', 'PATIENTS', 'DOCTORS', 'APPOINTMENTS', 'PHARMACY', 'BILLING', 'REPORTS', 'ADMIN_USERS', 'ADMIN_ROLES', 'ADMIN_PAGES', 'ADMIN_SETTINGS');

-- 5. Map Pages to Roles (ROLE_DOCTOR)
INSERT INTO role_pages (role_id, page_id)
SELECT r.id, p.id FROM roles r, ui_pages p 
WHERE r.name = 'ROLE_DOCTOR' 
AND p.page_key IN ('DOCTOR_DASHBOARD', 'DOCTOR_PATIENTS', 'DOCTORS', 'APPOINTMENTS', 'PHARMACY', 'DOCTOR_PRESCRIPTION');

-- 6. Map Pages to Roles (ROLE_RECEPTIONIST)
INSERT INTO role_pages (role_id, page_id)
SELECT r.id, p.id FROM roles r, ui_pages p 
WHERE r.name = 'ROLE_RECEPTIONIST' 
AND p.page_key IN ('RECEPTIONIST_DASHBOARD', 'RECEPTIONIST_PATIENTS', 'RECEPTIONIST_APPOINTMENTS', 'DOCTORS', 'BILLING');

-- 7. Map Pages to Roles (ROLE_SUPER_ADMIN)
INSERT INTO role_pages (role_id, page_id)
SELECT r.id, p.id FROM roles r, ui_pages p 
WHERE r.name = 'ROLE_SUPER_ADMIN' 
AND p.page_key IN ('ADMIN_DASHBOARD', 'SUPER_ADMIN');
