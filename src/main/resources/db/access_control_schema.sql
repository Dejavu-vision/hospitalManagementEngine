-- ============================================================
-- HMS ACCESS CONTROL SCHEMA - IDEMPOTENT (safe to re-run)
-- All statements use INSERT IGNORE so existing data is never
-- duplicated or deleted on subsequent executions.
-- ============================================================

-- 1. Ensure Roles exist
INSERT IGNORE INTO roles (name) VALUES
  ('ROLE_SUPER_ADMIN'),
  ('ROLE_ADMIN'),
  ('ROLE_DOCTOR'),
  ('ROLE_RECEPTIONIST');

-- 2. Seed ALL UI Pages (INSERT IGNORE = skip if page_key already exists)
INSERT IGNORE INTO ui_pages (page_key, route, display_name) VALUES
  -- Shared / Core
  ('ADMIN_DASHBOARD',            '/dashboard',              'Admin Dashboard'),
  ('RECEPTIONIST_DASHBOARD',     '/reception/dashboard',    'Receptionist Dashboard'),
  ('DOCTOR_DASHBOARD',           '/doctor/dashboard',       'Doctor Dashboard'),

  -- Main Modules
  ('PATIENTS',                   '/patients',               'Patient Management'),
  ('DOCTORS',                    '/doctors',                'Doctor Directory'),
  ('APPOINTMENTS',               '/appointments',           'Appointments'),
  ('PHARMACY',                   '/pharmacy',               'Pharmacy'),
  ('BILLING',                    '/billing',                'Billing'),
  ('REPORTS',                    '/reports',                'Reports'),

  -- Admin Specific
  ('ADMIN_USERS',                '/access',                 'Staff Management'),
  ('ADMIN_STAFF',                '/access',                 'Staff Management'),
  ('ADMIN_ROLES',                '/roles',                  'Role Management'),
  ('ADMIN_PAGES',                '/pages',                  'Page Config'),
  ('ADMIN_SETTINGS',             '/settings',               'System Settings'),

  -- Super Admin
  ('SUPER_ADMIN',                '/superadmin',             'Hospital Management'),

  -- Clinical / Doctor
  ('DOCTOR_PRESCRIPTION',        '/consultation',           'Prescription'),
  ('DOCTOR_PATIENTS',            '/patients',               'My Patients'),

  -- Receptionist screens
  ('RECEPTIONIST_PATIENTS',      '/reception/patients',     'Patient Intake'),
  ('RECEPTIONIST_APPOINTMENTS',  '/reception/appointments', 'Appointment Booking'),
  ('RECEPTIONIST_QUEUE',         '/reception/queue',        'Queue Management');

-- 3. Map Pages to Roles (ROLE_ADMIN)
INSERT IGNORE INTO role_pages (role_id, page_id)
  SELECT r.id, p.id FROM roles r, ui_pages p
  WHERE r.name = 'ROLE_ADMIN'
  AND p.page_key IN (
    'ADMIN_DASHBOARD', 'PATIENTS', 'DOCTORS', 'APPOINTMENTS',
    'PHARMACY', 'BILLING', 'REPORTS',
    'ADMIN_USERS', 'ADMIN_STAFF', 'ADMIN_ROLES', 'ADMIN_PAGES', 'ADMIN_SETTINGS'
  );

-- 4. Map Pages to Roles (ROLE_DOCTOR)
INSERT IGNORE INTO role_pages (role_id, page_id)
  SELECT r.id, p.id FROM roles r, ui_pages p
  WHERE r.name = 'ROLE_DOCTOR'
  AND p.page_key IN (
    'DOCTOR_DASHBOARD', 'DOCTOR_PATIENTS', 'DOCTORS',
    'APPOINTMENTS', 'PHARMACY', 'DOCTOR_PRESCRIPTION'
  );

-- 5. Map Pages to Roles (ROLE_RECEPTIONIST)
INSERT IGNORE INTO role_pages (role_id, page_id)
  SELECT r.id, p.id FROM roles r, ui_pages p
  WHERE r.name = 'ROLE_RECEPTIONIST'
  AND p.page_key IN (
    'RECEPTIONIST_DASHBOARD',
    'RECEPTIONIST_PATIENTS',
    'RECEPTIONIST_APPOINTMENTS',
    'RECEPTIONIST_QUEUE',
    'PATIENTS',
    'APPOINTMENTS',
    'DOCTORS',
    'BILLING'
  );

-- 6. Map Pages to Roles (ROLE_SUPER_ADMIN)
INSERT IGNORE INTO role_pages (role_id, page_id)
  SELECT r.id, p.id FROM roles r, ui_pages p
  WHERE r.name = 'ROLE_SUPER_ADMIN'
  AND p.page_key IN ('ADMIN_DASHBOARD', 'SUPER_ADMIN');
