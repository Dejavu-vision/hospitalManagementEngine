-- ============================================================
-- CuraMatrix HSM — PostgreSQL Seed Data
-- Runs automatically on startup (spring.sql.init.mode=always).
-- Idempotent: safe to run repeatedly (ON CONFLICT DO NOTHING).
-- Tables are created by Hibernate (ddl-auto=update) BEFORE this runs
-- (spring.jpa.defer-datasource-initialization=true).
--
-- Default login accounts (all tenant = default-hospital):
--   superadmin@curamatrix.com / admin123
--   admin@curamatrix.com      / admin123
--   doctor@curamatrix.com     / doctor123
--   reception@curamatrix.com  / reception123
-- ============================================================

-- ------------------------------------------------------------
-- ROLES (name stored as enum string)
-- ------------------------------------------------------------
INSERT INTO roles (name) VALUES
    ('ROLE_SUPER_ADMIN'),
    ('ROLE_ADMIN'),
    ('ROLE_DOCTOR'),
    ('ROLE_RECEPTIONIST')
ON CONFLICT (name) DO NOTHING;

-- ------------------------------------------------------------
-- DEFAULT TENANT (Hospital)
-- ------------------------------------------------------------
INSERT INTO tenants (
    tenant_key, hospital_name, subscription_plan,
    subscription_start, subscription_end,
    is_active, max_users, max_patients,
    contact_email, contact_phone, address, wake_word,
    created_at, updated_at
) VALUES (
    'default-hospital', 'Default Hospital', 'PREMIUM',
    CURRENT_DATE, CURRENT_DATE + INTERVAL '1 year',
    TRUE, 50, 10000,
    'admin@default-hospital.com', '0000000000', 'Default Address', 'hey matrix',
    NOW(), NOW()
) ON CONFLICT (tenant_key) DO NOTHING;

-- ------------------------------------------------------------
-- USERS (password hashes are BCrypt)
--   Admin@123    -> $2a$10$vAvowxrjhnEvjx/poO0UaePtPadiW7OnrOfA0AXrdz9OCsOBKO6Wy
--   admin123     -> $2a$10$EZorI1dSXCpkqqaCQtsIzeLil2.4pLe8G7bx//HtP6nmsCeBYM/S.
--   doctor123    -> $2a$10$Q6HDpLQmTVu8TxY1d.7tReHxf8x.v2Ct8UDWPVcpkNwKjWY0yyo9y
--   reception123 -> $2a$10$KXt6I5BF/xZd4OOiVcGyU.BpjXujpjyQqhu636Kxu4wlfy8mwVq.m
-- ------------------------------------------------------------
INSERT INTO users (tenant_id, email, password, full_name, phone, is_active, created_at, updated_at)
SELECT t.id, 'superadmin@curamatrix.com',
       '$2a$10$EZorI1dSXCpkqqaCQtsIzeLil2.4pLe8G7bx//HtP6nmsCeBYM/S.',
       'Super Administrator', '9999999999', TRUE, NOW(), NOW()
FROM tenants t WHERE t.tenant_key = 'default-hospital'
ON CONFLICT (email) DO UPDATE SET password = EXCLUDED.password, is_active = TRUE;

INSERT INTO users (tenant_id, email, password, full_name, phone, is_active, created_at, updated_at)
SELECT t.id, 'admin@curamatrix.com',
       '$2a$10$vAvowxrjhnEvjx/poO0UaePtPadiW7OnrOfA0AXrdz9OCsOBKO6Wy',
       'Hospital Admin', '9999999998', TRUE, NOW(), NOW()
FROM tenants t WHERE t.tenant_key = 'default-hospital'
ON CONFLICT (email) DO UPDATE SET password = EXCLUDED.password, is_active = TRUE;

INSERT INTO users (tenant_id, email, password, full_name, phone, is_active, created_at, updated_at)
SELECT t.id, 'doctor@curamatrix.com',
       '$2a$10$Q6HDpLQmTVu8TxY1d.7tReHxf8x.v2Ct8UDWPVcpkNwKjWY0yyo9y',
       'Dr. Rajesh Kumar', '9876543210', TRUE, NOW(), NOW()
FROM tenants t WHERE t.tenant_key = 'default-hospital'
ON CONFLICT (email) DO UPDATE SET password = EXCLUDED.password, is_active = TRUE;

INSERT INTO users (tenant_id, email, password, full_name, phone, is_active, created_at, updated_at)
SELECT t.id, 'reception@curamatrix.com',
       '$2a$10$KXt6I5BF/xZd4OOiVcGyU.BpjXujpjyQqhu636Kxu4wlfy8mwVq.m',
       'Neha Gupta', '9876543211', TRUE, NOW(), NOW()
FROM tenants t WHERE t.tenant_key = 'default-hospital'
ON CONFLICT (email) DO UPDATE SET password = EXCLUDED.password, is_active = TRUE;

-- ------------------------------------------------------------
-- USER <-> ROLE MAPPINGS
-- ------------------------------------------------------------
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'superadmin@curamatrix.com' AND r.name = 'ROLE_SUPER_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'admin@curamatrix.com' AND r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'doctor@curamatrix.com' AND r.name = 'ROLE_DOCTOR'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'reception@curamatrix.com' AND r.name = 'ROLE_RECEPTIONIST'
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------
-- DEPARTMENTS (unique per name + tenant)
-- ------------------------------------------------------------
INSERT INTO departments (tenant_id, name, description, is_active)
SELECT t.id, d.name, d.description, TRUE
FROM tenants t
CROSS JOIN (VALUES
    ('General Medicine', 'General health consultations and primary care'),
    ('Cardiology',       'Heart and cardiovascular system'),
    ('Orthopedics',      'Bones, joints, and muscles'),
    ('Pediatrics',       'Child healthcare and development'),
    ('Dermatology',      'Skin, hair, and nails'),
    ('ENT',              'Ear, Nose, and Throat'),
    ('Ophthalmology',    'Eye care and vision'),
    ('Gynecology',       'Women health and reproductive system'),
    ('Neurology',        'Brain and nervous system'),
    ('Dentistry',        'Dental care and oral health')
) AS d(name, description)
WHERE t.tenant_key = 'default-hospital'
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------
-- DOCTOR PROFILE for doctor@curamatrix.com
-- (doctors table is NOT tenant-aware; scoped via users.tenant_id)
-- ------------------------------------------------------------
INSERT INTO doctors (user_id, department_id, license_number, qualification, experience_years, consultation_fee)
SELECT u.id,
       (SELECT d.id FROM departments d
        JOIN tenants t ON d.tenant_id = t.id
        WHERE d.name = 'General Medicine' AND t.tenant_key = 'default-hospital' LIMIT 1),
       'MCI-GM-001', 'MBBS, MD (General Medicine)', 12, 500.00
FROM users u WHERE u.email = 'doctor@curamatrix.com'
ON CONFLICT (user_id) DO NOTHING;

-- ------------------------------------------------------------
-- RECEPTIONIST PROFILE for reception@curamatrix.com
-- ------------------------------------------------------------
INSERT INTO receptionists (user_id, employee_id, shift)
SELECT u.id, 'REC-001', 'MORNING'
FROM users u WHERE u.email = 'reception@curamatrix.com'
ON CONFLICT (user_id) DO NOTHING;

-- ------------------------------------------------------------
-- MEDICINES (shared catalog, not tenant-aware)
-- ------------------------------------------------------------
INSERT INTO medicines (name, generic_name, brand, strength, form, category, is_active)
SELECT m.name, m.generic_name, m.brand, m.strength, m.form, m.category, TRUE
FROM (VALUES
    ('Paracetamol',        'Acetaminophen',          'Crocin',   '500mg',     'TABLET',  'Analgesic'),
    ('Paracetamol',        'Acetaminophen',          'Dolo',     '650mg',     'TABLET',  'Analgesic'),
    ('Amoxicillin',        'Amoxicillin Trihydrate', 'Mox',      '500mg',     'CAPSULE', 'Antibiotic'),
    ('Azithromycin',       'Azithromycin Dihydrate', 'Azithral', '500mg',     'TABLET',  'Antibiotic'),
    ('Cetirizine',         'Cetirizine HCl',         'Cetzine',  '10mg',      'TABLET',  'Antihistamine'),
    ('Omeprazole',         'Omeprazole',             'Omez',     '20mg',      'CAPSULE', 'Antacid'),
    ('Pantoprazole',       'Pantoprazole Sodium',    'Pan',      '40mg',      'TABLET',  'Antacid'),
    ('Metformin',          'Metformin HCl',          'Glycomet', '500mg',     'TABLET',  'Antidiabetic'),
    ('Amlodipine',         'Amlodipine Besylate',    'Amlong',   '5mg',       'TABLET',  'Antihypertensive'),
    ('Ibuprofen',          'Ibuprofen',              'Brufen',   '400mg',     'TABLET',  'NSAID'),
    ('Diclofenac',         'Diclofenac Sodium',      'Voveran',  '50mg',      'TABLET',  'NSAID'),
    ('Multivitamin',       'Multivitamin',           'Becosules', NULL,       'CAPSULE', 'Supplement')
) AS m(name, generic_name, brand, strength, form, category)
WHERE NOT EXISTS (
    SELECT 1 FROM medicines x
    WHERE x.name = m.name AND COALESCE(x.strength,'') = COALESCE(m.strength,'') AND COALESCE(x.brand,'') = COALESCE(m.brand,'')
);
