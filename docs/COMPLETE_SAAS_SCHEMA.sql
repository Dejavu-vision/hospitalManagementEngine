-- ============================================================
-- CuraMatrix Hospital Management System (HSM)
-- Complete Multi-Tenant SaaS Database Schema
-- Version: 2.0.0 (SaaS Edition)
-- Date: 2026-03-11
-- Database: MySQL 8.x
--
-- Execute: mysql -u root -p < docs/COMPLETE_SAAS_SCHEMA.sql
-- ============================================================

-- ------------------------------------------------------------
-- 1. CREATE DATABASE
-- ------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS hospitalsystems
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE hospitalsystems;

-- ------------------------------------------------------------
-- 2. DROP TABLES (Clean slate)
-- ------------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS tenant_audit_log;
DROP TABLE IF EXISTS tenant_usage_stats;
DROP TABLE IF EXISTS tenant_subscriptions;
DROP TABLE IF EXISTS billing_items;
DROP TABLE IF EXISTS billings;
DROP TABLE IF EXISTS prescriptions;
DROP TABLE IF EXISTS diagnoses;
DROP TABLE IF EXISTS appointments;
DROP TABLE IF EXISTS medicine_inventory;
DROP TABLE IF EXISTS medicines;
DROP TABLE IF EXISTS patients;
DROP TABLE IF EXISTS receptionists;
DROP TABLE IF EXISTS doctors;
DROP TABLE IF EXISTS departments;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS tenants;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- PART 1: MULTI-TENANT CORE TABLES
-- ============================================================

-- ------------------------------------------------------------
-- 3. TENANTS (Hospitals)
-- ------------------------------------------------------------
CREATE TABLE tenants (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_key          VARCHAR(100) UNIQUE NOT NULL,
    hospital_name       VARCHAR(255) NOT NULL,
    subscription_plan   VARCHAR(50) NOT NULL,
    subscription_start  DATE NOT NULL,
    subscription_end    DATE NOT NULL,
    is_active           BOOLEAN DEFAULT TRUE,
    max_users           INT DEFAULT 50,
    max_patients        INT DEFAULT 10000,
    contact_email       VARCHAR(255) NOT NULL,
    contact_phone       VARCHAR(20),
    address             TEXT,
    logo                VARCHAR(500),
    settings            JSON,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_tenants_key (tenant_key),
    INDEX idx_tenants_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- PART 2: USER MANAGEMENT TABLES
-- ============================================================

-- ------------------------------------------------------------
-- 4. ROLES
-- ------------------------------------------------------------
CREATE TABLE roles (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    name    VARCHAR(50) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 5. USERS (Tenant-aware)
-- ------------------------------------------------------------
CREATE TABLE users (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT          NOT NULL,
    email       VARCHAR(255)    NOT NULL,
    password    VARCHAR(255)    NOT NULL,
    full_name   VARCHAR(255)    NOT NULL,
    phone       VARCHAR(20)     NULL,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email_tenant (email, tenant_id),
    INDEX idx_users_tenant (tenant_id),
    INDEX idx_users_active (is_active),
    INDEX idx_users_tenant_email (tenant_id, email),
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 6. USER_ROLES (Join Table)
-- ------------------------------------------------------------
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,

    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- PART 3: HOSPITAL STRUCTURE TABLES
-- ============================================================

-- ------------------------------------------------------------
-- 7. DEPARTMENTS (Tenant-aware)
-- ------------------------------------------------------------
CREATE TABLE departments (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT          NOT NULL,
    name        VARCHAR(255)    NOT NULL,
    description VARCHAR(500)    NULL,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,

    PRIMARY KEY (id),
    INDEX idx_departments_tenant (tenant_id),
    INDEX idx_departments_active (is_active),
    CONSTRAINT fk_departments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 8. DOCTORS (Tenant-aware)
-- ------------------------------------------------------------
CREATE TABLE doctors (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    tenant_id           BIGINT          NOT NULL,
    user_id             BIGINT          NOT NULL,
    department_id       BIGINT          NULL,
    specialization      VARCHAR(255)    NOT NULL,
    license_number      VARCHAR(100)    NOT NULL,
    qualification       VARCHAR(255)    NULL,
    experience_years    INT             NULL,
    consultation_fee    DECIMAL(10,2)   NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_doctors_user (user_id),
    INDEX idx_doctors_tenant (tenant_id),
    INDEX idx_doctors_department (department_id),
    CONSTRAINT fk_doctors_tenant     FOREIGN KEY (tenant_id)     REFERENCES tenants (id),
    CONSTRAINT fk_doctors_user       FOREIGN KEY (user_id)       REFERENCES users (id),
    CONSTRAINT fk_doctors_department FOREIGN KEY (department_id) REFERENCES departments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 9. RECEPTIONISTS (Tenant-aware)
-- ------------------------------------------------------------
CREATE TABLE receptionists (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    employee_id VARCHAR(50) NULL,
    shift       VARCHAR(20) NULL COMMENT 'MORNING, AFTERNOON, NIGHT',

    PRIMARY KEY (id),
    UNIQUE KEY uk_receptionists_user (user_id),
    INDEX idx_receptionists_tenant (tenant_id),
    CONSTRAINT fk_receptionists_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_receptionists_user   FOREIGN KEY (user_id)   REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- PART 4: PATIENT & APPOINTMENT TABLES
-- ============================================================

-- ------------------------------------------------------------
-- 10. PATIENTS (Tenant-aware)
-- ------------------------------------------------------------
CREATE TABLE patients (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    tenant_id               BIGINT          NOT NULL,
    first_name              VARCHAR(100)    NOT NULL,
    last_name               VARCHAR(100)    NOT NULL,
    date_of_birth           DATE            NOT NULL,
    gender                  VARCHAR(10)     NOT NULL COMMENT 'MALE, FEMALE, OTHER',
    phone                   VARCHAR(20)     NOT NULL,
    email                   VARCHAR(255)    NULL,
    address                 TEXT            NULL,
    blood_group             VARCHAR(15)     NULL,
    emergency_contact_name  VARCHAR(255)    NULL,
    emergency_contact_phone VARCHAR(20)     NULL,
    allergies               TEXT            NULL,
    medical_history         TEXT            NULL,
    registered_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    registered_by           BIGINT          NULL,

    PRIMARY KEY (id),
    INDEX idx_patients_tenant (tenant_id),
    INDEX idx_patients_name (first_name, last_name),
    INDEX idx_patients_phone (phone),
    INDEX idx_patients_tenant_phone (tenant_id, phone),
    CONSTRAINT fk_patients_tenant        FOREIGN KEY (tenant_id)    REFERENCES tenants (id),
    CONSTRAINT fk_patients_registered_by FOREIGN KEY (registered_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 11. APPOINTMENTS (Tenant-aware)
-- ------------------------------------------------------------
CREATE TABLE appointments (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id           BIGINT      NOT NULL,
    patient_id          BIGINT      NOT NULL,
    doctor_id           BIGINT      NOT NULL,
    booked_by           BIGINT      NOT NULL,
    appointment_date    DATE        NOT NULL,
    appointment_time    TIME        NULL,
    type                VARCHAR(20) NOT NULL COMMENT 'SCHEDULED, WALK_IN',
    token_number        INT         NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    notes               TEXT        NULL,
    created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_appointments_tenant (tenant_id),
    INDEX idx_appointments_date (appointment_date),
    INDEX idx_appointments_tenant_date (tenant_id, appointment_date),
    INDEX idx_appointments_tenant_doctor (tenant_id, doctor_id, appointment_date),
    INDEX idx_appointments_patient (patient_id),
    INDEX idx_appointments_status (status),
    CONSTRAINT fk_appointments_tenant    FOREIGN KEY (tenant_id)  REFERENCES tenants (id),
    CONSTRAINT fk_appointments_patient   FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_appointments_doctor    FOREIGN KEY (doctor_id)  REFERENCES doctors (id),
    CONSTRAINT fk_appointments_booked_by FOREIGN KEY (booked_by)  REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- PART 5: CLINICAL TABLES
-- ============================================================

-- ------------------------------------------------------------
-- 12. DIAGNOSES (Tenant-aware)
-- ------------------------------------------------------------
CREATE TABLE diagnoses (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT      NOT NULL,
    appointment_id  BIGINT      NOT NULL,
    doctor_id       BIGINT      NOT NULL,
    symptoms        TEXT        NOT NULL,
    diagnosis       TEXT        NOT NULL,
    clinical_notes  TEXT        NULL,
    severity        VARCHAR(20) NULL,
    follow_up_date  DATE        NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_diagnoses_appointment (appointment_id),
    INDEX idx_diagnoses_tenant (tenant_id),
    INDEX idx_diagnoses_doctor (doctor_id),
    CONSTRAINT fk_diagnoses_tenant      FOREIGN KEY (tenant_id)      REFERENCES tenants (id),
    CONSTRAINT fk_diagnoses_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT fk_diagnoses_doctor      FOREIGN KEY (doctor_id)      REFERENCES doctors (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 13. MEDICINES
-- ------------------------------------------------------------
CREATE TABLE medicines (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(255)    NOT NULL,
    generic_name    VARCHAR(255)    NULL,
    brand           VARCHAR(255)    NULL,
    strength        VARCHAR(50)     NULL,
    form            VARCHAR(50)     NULL,
    category        VARCHAR(100)    NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,

    PRIMARY KEY (id),
    INDEX idx_medicines_name (name),
    INDEX idx_medicines_generic (generic_name),
    INDEX idx_medicines_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 14. PRESCRIPTIONS (Tenant-aware)
-- ------------------------------------------------------------
CREATE TABLE prescriptions (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT          NOT NULL,
    diagnosis_id    BIGINT          NOT NULL,
    medicine_id     BIGINT          NOT NULL,
    dosage          VARCHAR(100)    NOT NULL,
    frequency       VARCHAR(100)    NOT NULL,
    duration_days   INT             NOT NULL,
    instructions    TEXT            NULL,

    PRIMARY KEY (id),
    INDEX idx_prescriptions_tenant (tenant_id),
    INDEX idx_prescriptions_diagnosis (diagnosis_id),
    INDEX idx_prescriptions_medicine (medicine_id),
    CONSTRAINT fk_prescriptions_tenant    FOREIGN KEY (tenant_id)    REFERENCES tenants (id),
    CONSTRAINT fk_prescriptions_diagnosis FOREIGN KEY (diagnosis_id) REFERENCES diagnoses (id) ON DELETE CASCADE,
    CONSTRAINT fk_prescriptions_medicine  FOREIGN KEY (medicine_id)  REFERENCES medicines (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 15. MEDICINE_INVENTORY
-- ------------------------------------------------------------
CREATE TABLE medicine_inventory (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    medicine_id     BIGINT          NOT NULL,
    quantity        INT             NOT NULL DEFAULT 0,
    unit_price      DECIMAL(10,2)   NOT NULL,
    expiry_date     DATE            NOT NULL,
    batch_number    VARCHAR(100)    NULL,
    last_updated    TIMESTAMP       NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_inventory_medicine (medicine_id),
    INDEX idx_inventory_expiry (expiry_date),
    CONSTRAINT fk_inventory_medicine FOREIGN KEY (medicine_id) REFERENCES medicines (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- PART 6: BILLING TABLES
-- ============================================================

-- ------------------------------------------------------------
-- 16. BILLINGS (Tenant-aware)
-- ------------------------------------------------------------
CREATE TABLE billings (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT          NOT NULL,
    appointment_id  BIGINT          NULL,
    patient_id      BIGINT          NOT NULL,
    invoice_number  VARCHAR(50)     NOT NULL,
    total_amount    DECIMAL(12,2)   NOT NULL,
    discount        DECIMAL(10,2)   NOT NULL DEFAULT 0.00,
    tax             DECIMAL(10,2)   NOT NULL DEFAULT 0.00,
    net_amount      DECIMAL(12,2)   NOT NULL,
    payment_status  VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    payment_method  VARCHAR(20)     NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_billings_invoice_tenant (invoice_number, tenant_id),
    INDEX idx_billings_tenant (tenant_id),
    INDEX idx_billings_appointment (appointment_id),
    INDEX idx_billings_patient (patient_id),
    INDEX idx_billings_status (payment_status),
    CONSTRAINT fk_billings_tenant      FOREIGN KEY (tenant_id)      REFERENCES tenants (id),
    CONSTRAINT fk_billings_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT fk_billings_patient     FOREIGN KEY (patient_id)     REFERENCES patients (id),
    CONSTRAINT fk_billings_created_by  FOREIGN KEY (created_by)     REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 17. BILLING_ITEMS (Tenant-aware)
-- ------------------------------------------------------------
CREATE TABLE billing_items (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT          NOT NULL,
    billing_id  BIGINT          NOT NULL,
    description VARCHAR(255)    NOT NULL,
    amount      DECIMAL(10,2)   NOT NULL,
    quantity    INT             NOT NULL DEFAULT 1,
    item_type   VARCHAR(20)     NOT NULL,

    PRIMARY KEY (id),
    INDEX idx_billing_items_tenant (tenant_id),
    INDEX idx_billing_items_billing (billing_id),
    CONSTRAINT fk_billing_items_tenant  FOREIGN KEY (tenant_id)  REFERENCES tenants (id),
    CONSTRAINT fk_billing_items_billing FOREIGN KEY (billing_id) REFERENCES billings (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- PART 7: SAAS MANAGEMENT TABLES
-- ============================================================

-- ------------------------------------------------------------
-- 18. TENANT_SUBSCRIPTIONS
-- ------------------------------------------------------------
CREATE TABLE tenant_subscriptions (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id       BIGINT NOT NULL,
    plan_name       VARCHAR(50) NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    billing_cycle   VARCHAR(20) NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    status          VARCHAR(20) NOT NULL,
    payment_method  VARCHAR(50),
    transaction_id  VARCHAR(255),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    INDEX idx_subscriptions_tenant (tenant_id),
    INDEX idx_subscriptions_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 19. TENANT_USAGE_STATS
-- ------------------------------------------------------------
CREATE TABLE tenant_usage_stats (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id           BIGINT NOT NULL,
    stat_date           DATE NOT NULL,
    total_users         INT DEFAULT 0,
    total_patients      INT DEFAULT 0,
    total_appointments  INT DEFAULT 0,
    total_diagnoses     INT DEFAULT 0,
    api_calls           INT DEFAULT 0,
    storage_used_mb     INT DEFAULT 0,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    UNIQUE KEY uk_tenant_stat_date (tenant_id, stat_date),
    INDEX idx_stats_tenant_date (tenant_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 20. TENANT_AUDIT_LOG
-- ------------------------------------------------------------
CREATE TABLE tenant_audit_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id       BIGINT NOT NULL,
    user_id         BIGINT,
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(50),
    entity_id       BIGINT,
    details         JSON,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    INDEX idx_audit_tenant (tenant_id),
    INDEX idx_audit_date (created_at),
    INDEX idx_audit_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- PART 8: SEED DATA
-- ============================================================

-- ------------------------------------------------------------
-- ROLES
-- ------------------------------------------------------------
INSERT INTO roles (name) VALUES
    ('ROLE_SUPER_ADMIN'),
    ('ROLE_ADMIN'),
    ('ROLE_DOCTOR'),
    ('ROLE_RECEPTIONIST');

-- ------------------------------------------------------------
-- DEFAULT TENANT (for existing/demo data)
-- ------------------------------------------------------------
INSERT INTO tenants (
    tenant_key, hospital_name, subscription_plan, 
    subscription_start, subscription_end,
    is_active, max_users, max_patients,
    contact_email, contact_phone, address
) VALUES (
    'default-hospital',
    'Default Hospital',
    'PREMIUM',
    CURDATE(),
    DATE_ADD(CURDATE(), INTERVAL 1 YEAR),
    TRUE,
    -1,  -- Unlimited
    -1,  -- Unlimited
    'admin@default-hospital.com',
    '0000000000',
    'Default Address'
);

SET @default_tenant_id = LAST_INSERT_ID();

-- ------------------------------------------------------------
-- SUPER ADMIN USER (Platform Level)
-- Password: admin123
-- ------------------------------------------------------------
INSERT INTO users (tenant_id, email, password, full_name, phone, is_active)
VALUES (
    @default_tenant_id,
    'superadmin@curamatrix.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'Super Administrator',
    '9999999999',
    TRUE
);

INSERT INTO user_roles (user_id, role_id)
VALUES (
    (SELECT id FROM users WHERE email = 'superadmin@curamatrix.com'),
    (SELECT id FROM roles WHERE name = 'ROLE_SUPER_ADMIN')
);

-- ------------------------------------------------------------
-- DEFAULT HOSPITAL ADMIN
-- Password: admin123
-- ------------------------------------------------------------
INSERT INTO users (tenant_id, email, password, full_name, phone, is_active)
VALUES (
    @default_tenant_id,
    'admin@curamatrix.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'Hospital Admin',
    '9999999998',
    TRUE
);

INSERT INTO user_roles (user_id, role_id)
VALUES (
    (SELECT id FROM users WHERE email = 'admin@curamatrix.com'),
    (SELECT id FROM roles WHERE name = 'ROLE_ADMIN')
);

-- ------------------------------------------------------------
-- DEPARTMENTS (Default Hospital)
-- ------------------------------------------------------------
INSERT INTO departments (tenant_id, name, description, is_active) VALUES
    (@default_tenant_id, 'General Medicine',    'General health consultations and primary care',    TRUE),
    (@default_tenant_id, 'Cardiology',          'Heart and cardiovascular system',                  TRUE),
    (@default_tenant_id, 'Orthopedics',         'Bones, joints, and muscles',                       TRUE),
    (@default_tenant_id, 'Pediatrics',          'Child healthcare and development',                 TRUE),
    (@default_tenant_id, 'Dermatology',         'Skin, hair, and nails',                            TRUE),
    (@default_tenant_id, 'ENT',                 'Ear, Nose, and Throat',                            TRUE),
    (@default_tenant_id, 'Ophthalmology',       'Eye care and vision',                              TRUE),
    (@default_tenant_id, 'Gynecology',          'Women health and reproductive system',             TRUE),
    (@default_tenant_id, 'Neurology',           'Brain and nervous system',                         TRUE),
    (@default_tenant_id, 'Dentistry',           'Dental care and oral health',                      TRUE);

-- ------------------------------------------------------------
-- SAMPLE DOCTOR (Default Hospital)
-- Password: doctor123
-- ------------------------------------------------------------
INSERT INTO users (tenant_id, email, password, full_name, phone, is_active)
VALUES (
    @default_tenant_id,
    'doctor@curamatrix.com',
    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
    'Dr. Rajesh Kumar',
    '9876543210',
    TRUE
);

INSERT INTO user_roles (user_id, role_id)
VALUES (
    (SELECT id FROM users WHERE email = 'doctor@curamatrix.com'),
    (SELECT id FROM roles WHERE name = 'ROLE_DOCTOR')
);

INSERT INTO doctors (tenant_id, user_id, department_id, specialization, license_number, qualification, experience_years, consultation_fee)
VALUES (
    @default_tenant_id,
    (SELECT id FROM users WHERE email = 'doctor@curamatrix.com'),
    (SELECT id FROM departments WHERE name = 'General Medicine' AND tenant_id = @default_tenant_id LIMIT 1),
    'General Medicine',
    'MCI-GM-001',
    'MBBS, MD (General Medicine)',
    12,
    500.00
);

-- ------------------------------------------------------------
-- SAMPLE RECEPTIONIST (Default Hospital)
-- Password: reception123
-- ------------------------------------------------------------
INSERT INTO users (tenant_id, email, password, full_name, phone, is_active)
VALUES (
    @default_tenant_id,
    'reception@curamatrix.com',
    '$2a$10$EkRAGTGYSg7HnP3UQ0aBxO7yQOEKagDnqJXuFP3Tl1E.Vx/vK5jK6',
    'Neha Gupta',
    '9876543211',
    TRUE
);

INSERT INTO user_roles (user_id, role_id)
VALUES (
    (SELECT id FROM users WHERE email = 'reception@curamatrix.com'),
    (SELECT id FROM roles WHERE name = 'ROLE_RECEPTIONIST')
);

INSERT INTO receptionists (tenant_id, user_id, employee_id, shift)
VALUES (
    @default_tenant_id,
    (SELECT id FROM users WHERE email = 'reception@curamatrix.com'),
    'REC-001',
    'MORNING'
);

-- ------------------------------------------------------------
-- MEDICINES (Shared across all tenants)
-- ------------------------------------------------------------
INSERT INTO medicines (name, generic_name, brand, strength, form, category, is_active) VALUES
    ('Paracetamol',         'Acetaminophen',            'Crocin',       '500mg',        'TABLET',       'Analgesic',        TRUE),
    ('Paracetamol',         'Acetaminophen',            'Dolo',         '650mg',        'TABLET',       'Analgesic',        TRUE),
    ('Paracetamol Syrup',   'Acetaminophen',            'Calpol',       '250mg/5ml',    'SYRUP',        'Analgesic',        TRUE),
    ('Amoxicillin',         'Amoxicillin Trihydrate',   'Mox',          '500mg',        'CAPSULE',      'Antibiotic',       TRUE),
    ('Amoxicillin',         'Amoxicillin Trihydrate',   'Novamox',      '250mg',        'CAPSULE',      'Antibiotic',       TRUE),
    ('Azithromycin',        'Azithromycin Dihydrate',   'Azithral',     '500mg',        'TABLET',       'Antibiotic',       TRUE),
    ('Cetirizine',          'Cetirizine HCl',           'Cetzine',      '10mg',         'TABLET',       'Antihistamine',    TRUE),
    ('Omeprazole',          'Omeprazole',               'Omez',         '20mg',         'CAPSULE',      'Antacid',          TRUE),
    ('Pantoprazole',        'Pantoprazole Sodium',      'Pan',          '40mg',         'TABLET',       'Antacid',          TRUE),
    ('Metformin',           'Metformin HCl',            'Glycomet',     '500mg',        'TABLET',       'Antidiabetic',     TRUE),
    ('Metformin',           'Metformin HCl',            'Glycomet',     '1000mg',       'TABLET',       'Antidiabetic',     TRUE),
    ('Glimepiride',         'Glimepiride',              'Amaryl',       '1mg',          'TABLET',       'Antidiabetic',     TRUE),
    ('Glimepiride',         'Glimepiride',              'Amaryl',       '2mg',          'TABLET',       'Antidiabetic',     TRUE),
    ('Amlodipine',          'Amlodipine Besylate',      'Amlong',       '5mg',          'TABLET',       'Antihypertensive', TRUE),
    ('Atenolol',            'Atenolol',                 'Aten',         '50mg',         'TABLET',       'Beta Blocker',     TRUE),
    ('Ibuprofen',           'Ibuprofen',                'Brufen',       '400mg',        'TABLET',       'NSAID',            TRUE),
    ('Diclofenac',          'Diclofenac Sodium',        'Voveran',      '50mg',         'TABLET',       'NSAID',            TRUE),
    ('Ranitidine',          'Ranitidine HCl',           'Rantac',       '150mg',        'TABLET',       'Antacid',          TRUE),
    ('Ciprofloxacin',       'Ciprofloxacin HCl',        'Ciplox',       '500mg',        'TABLET',       'Antibiotic',       TRUE),
    ('Montelukast',         'Montelukast Sodium',       'Montair',      '10mg',         'TABLET',       'Anti-asthmatic',   TRUE),
    ('Salbutamol Inhaler',  'Salbutamol Sulphate',      'Asthalin',     '100mcg',       'INHALER',      'Bronchodilator',   TRUE),
    ('Prednisolone',        'Prednisolone',             'Wysolone',     '10mg',         'TABLET',       'Corticosteroid',   TRUE),
    ('Multivitamin',        'Multivitamin',             'Becosules',    NULL,           'CAPSULE',      'Supplement',       TRUE),
    ('Calcium + Vitamin D', 'Calcium Carbonate + D3',   'Shelcal',      '500mg',        'TABLET',       'Supplement',       TRUE),
    ('Iron + Folic Acid',   'Ferrous Sulphate + FA',    'Autrin',       '100mg',        'TABLET',       'Supplement',       TRUE);

-- ------------------------------------------------------------
-- MEDICINE INVENTORY
-- ------------------------------------------------------------
INSERT INTO medicine_inventory (medicine_id, quantity, unit_price, expiry_date, batch_number) VALUES
    (1,  500,    2.50,   '2027-06-30', 'BATCH-PCM500-001'),
    (2,  300,    3.00,   '2027-06-30', 'BATCH-PCM650-001'),
    (3,  100,    45.00,  '2026-12-31', 'BATCH-PCMSYR-001'),
    (4,  200,    8.50,   '2027-03-31', 'BATCH-AMOX500-001'),
    (5,  150,    5.50,   '2027-03-31', 'BATCH-AMOX250-001'),
    (6,  100,    12.00,  '2027-09-30', 'BATCH-AZITH-001'),
    (7,  400,    1.50,   '2027-12-31', 'BATCH-CETZ-001'),
    (8,  250,    3.50,   '2027-06-30', 'BATCH-OMEZ-001'),
    (9,  200,    4.00,   '2027-06-30', 'BATCH-PAN-001'),
    (10, 300,    2.00,   '2027-09-30', 'BATCH-MET500-001'),
    (11, 200,    3.50,   '2027-09-30', 'BATCH-MET1000-001'),
    (12, 100,    4.50,   '2027-12-31', 'BATCH-GLIM1-001'),
    (13, 100,    6.00,   '2027-12-31', 'BATCH-GLIM2-001'),
    (14, 200,    2.50,   '2027-06-30', 'BATCH-AMLO-001'),
    (15, 150,    3.00,   '2027-06-30', 'BATCH-ATEN-001'),
    (16, 300,    2.00,   '2027-03-31', 'BATCH-IBU-001'),
    (17, 200,    3.00,   '2027-03-31', 'BATCH-DICLO-001'),
    (18, 250,    2.50,   '2027-06-30', 'BATCH-RANT-001'),
    (19, 100,    5.00,   '2027-09-30', 'BATCH-CIPRO-001'),
    (20, 150,    6.50,   '2027-12-31', 'BATCH-MONT-001'),
    (21, 50,     120.00, '2027-12-31', 'BATCH-SALB-001'),
    (22, 200,    3.50,   '2027-06-30', 'BATCH-PRED-001'),
    (23, 300,    4.00,   '2028-03-31', 'BATCH-MULTI-001'),
    (24, 250,    5.50,   '2028-03-31', 'BATCH-CALC-001'),
    (25, 200,    3.00,   '2028-03-31', 'BATCH-IRON-001');

-- Low stock test data
INSERT INTO medicine_inventory (medicine_id, quantity, unit_price, expiry_date, batch_number) VALUES
    (1, 3, 2.50, '2026-04-15', 'BATCH-PCM500-OLD');

-- Expiring soon test data
INSERT INTO medicine_inventory (medicine_id, quantity, unit_price, expiry_date, batch_number) VALUES
    (6, 50, 12.00, '2026-04-01', 'BATCH-AZITH-OLD');

-- ============================================================
-- PART 9: VERIFICATION QUERIES
-- ============================================================

-- Check all tables created
SELECT TABLE_NAME, TABLE_ROWS, CREATE_TIME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'hospitalsystems'
ORDER BY TABLE_NAME;

-- Check tenants
SELECT id, tenant_key, hospital_name, subscription_plan, is_active
FROM tenants;

-- Check users and roles
SELECT u.id, u.tenant_id, u.email, u.full_name, r.name AS role
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id
ORDER BY u.id;

-- Check departments
SELECT id, tenant_id, name, is_active
FROM departments
ORDER BY tenant_id, id;

-- Check doctors
SELECT d.id, d.tenant_id, u.full_name, dep.name AS department, d.specialization
FROM doctors d
JOIN users u ON d.user_id = u.id
LEFT JOIN departments dep ON d.department_id = dep.id;

-- Check medicines count
SELECT COUNT(*) AS total_medicines FROM medicines;

-- Check inventory count
SELECT COUNT(*) AS total_inventory_records FROM medicine_inventory;

-- ============================================================
-- DONE! Multi-Tenant SaaS Database Ready
-- ============================================================

SELECT 'Multi-Tenant SaaS Database Created Successfully!' AS status,
       (SELECT COUNT(*) FROM tenants) AS total_tenants,
       (SELECT COUNT(*) FROM users) AS total_users,
       (SELECT COUNT(*) FROM departments) AS total_departments,
       (SELECT COUNT(*) FROM medicines) AS total_medicines;
