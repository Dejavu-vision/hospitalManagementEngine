-- Seed local database with HOSPITAL_001 tenant
INSERT INTO tenants (
    tenant_key, hospital_name, subscription_plan, 
    subscription_start, subscription_end,
    is_active, max_users, max_patients,
    contact_email, contact_phone, address
) VALUES (
    'HOSPITAL_001',
    'Curametix Hospital',
    'PREMIUM',
    '2026-01-01',
    '2036-01-01',
    TRUE,
    -1,
    -1,
    'fullaccess@curametix.com',
    '0000000000',
    'Hospital 001 Address'
) ON DUPLICATE KEY UPDATE hospital_name='Curametix Hospital';

-- Seed General Medicine department for the new tenant
INSERT INTO departments (tenant_id, name, description, is_active)
VALUES (
    (SELECT id FROM tenants WHERE tenant_key = 'HOSPITAL_001'),
    'General Medicine',
    'General health consultations and primary care',
    TRUE
) ON DUPLICATE KEY UPDATE name=name;

-- Seed the fullaccess@curametix.com user mapped to HOSPITAL_001
INSERT INTO users (tenant_id, email, password, full_name, phone, is_active)
VALUES (
    (SELECT id FROM tenants WHERE tenant_key = 'HOSPITAL_001'),
    'fullaccess@curametix.com',
    '$2a$10$huro/BeMJASkDI3hot8Tb..Ypb2QbB1l1bw2QDleSFcbNxhXOGCsO',
    'Full Access User',
    '0000000000',
    TRUE
) ON DUPLICATE KEY UPDATE full_name='Full Access User';

-- Map the user to ADMIN, DOCTOR, and RECEPTIONIST roles
INSERT INTO user_roles (user_id, role_id)
SELECT 
    (SELECT id FROM users WHERE email = 'fullaccess@curametix.com' AND tenant_id = (SELECT id FROM tenants WHERE tenant_key = 'HOSPITAL_001')),
    r.id
FROM roles r
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_RECEPTIONIST')
ON DUPLICATE KEY UPDATE user_id=user_id;
