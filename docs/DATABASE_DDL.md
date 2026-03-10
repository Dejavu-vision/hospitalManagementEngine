# CuraMatrix HSM -- Database DDL Script
# Ready-to-Execute MySQL Schema

**Database:** `hospitalsystems`
**Engine:** MySQL 8.x
**Charset:** utf8mb4

---

## How to Execute

### Option 1: MySQL CLI

```bash
mysql -u root -p < docs/schema.sql
```

### Option 2: MySQL Workbench / DBeaver

Copy the SQL from [Section 1](#1-full-ddl-script) and execute in your SQL editor.

### Option 3: From within MySQL shell

```sql
source /path/to/docs/schema.sql;
```

---

## 1. Full DDL Script

```sql
-- ============================================================
-- CuraMatrix Hospital Management System (HSM)
-- Database Schema DDL
-- Version: 1.0.0
-- Date: 2026-03-11
-- Database: MySQL 8.x
-- ============================================================

-- ------------------------------------------------------------
-- 1. CREATE DATABASE
-- ------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS hospitalsystems
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE hospitalsystems;

-- ------------------------------------------------------------
-- 2. DROP TABLES (in reverse dependency order for clean re-run)
-- ------------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 0;

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

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- CORE TABLES
-- ============================================================

-- ------------------------------------------------------------
-- 3. USERS
-- ------------------------------------------------------------
CREATE TABLE users (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255)    NOT NULL,
    password    VARCHAR(255)    NOT NULL,
    full_name   VARCHAR(255)    NOT NULL,
    phone       VARCHAR(20)     NULL,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    INDEX idx_users_active (is_active),
    INDEX idx_users_full_name (full_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
-- 5. USER_ROLES (Join Table)
-- ------------------------------------------------------------
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,

    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 6. DEPARTMENTS
-- ------------------------------------------------------------
CREATE TABLE departments (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255)    NOT NULL,
    description VARCHAR(500)    NULL,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,

    PRIMARY KEY (id),
    INDEX idx_departments_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 7. DOCTORS
-- ------------------------------------------------------------
CREATE TABLE doctors (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    user_id             BIGINT          NOT NULL,
    department_id       BIGINT          NULL,
    specialization      VARCHAR(255)    NOT NULL,
    license_number      VARCHAR(100)    NOT NULL,
    qualification       VARCHAR(255)    NULL,
    experience_years    INT             NULL,
    consultation_fee    DECIMAL(10,2)   NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_doctors_user (user_id),
    UNIQUE KEY uk_doctors_license (license_number),
    INDEX idx_doctors_department (department_id),
    CONSTRAINT fk_doctors_user       FOREIGN KEY (user_id)       REFERENCES users (id),
    CONSTRAINT fk_doctors_department FOREIGN KEY (department_id) REFERENCES departments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 8. RECEPTIONISTS
-- ------------------------------------------------------------
CREATE TABLE receptionists (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    employee_id VARCHAR(50) NULL,
    shift       VARCHAR(20) NULL COMMENT 'MORNING, AFTERNOON, NIGHT',

    PRIMARY KEY (id),
    UNIQUE KEY uk_receptionists_user (user_id),
    UNIQUE KEY uk_receptionists_employee (employee_id),
    CONSTRAINT fk_receptionists_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- PATIENT & APPOINTMENT TABLES
-- ============================================================

-- ------------------------------------------------------------
-- 9. PATIENTS
-- ------------------------------------------------------------
CREATE TABLE patients (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    first_name              VARCHAR(100)    NOT NULL,
    last_name               VARCHAR(100)    NOT NULL,
    date_of_birth           DATE            NOT NULL,
    gender                  VARCHAR(10)     NOT NULL COMMENT 'MALE, FEMALE, OTHER',
    phone                   VARCHAR(20)     NOT NULL,
    email                   VARCHAR(255)    NULL,
    address                 TEXT            NULL,
    blood_group             VARCHAR(15)     NULL COMMENT 'A_POSITIVE, A_NEGATIVE, B_POSITIVE, B_NEGATIVE, O_POSITIVE, O_NEGATIVE, AB_POSITIVE, AB_NEGATIVE',
    emergency_contact_name  VARCHAR(255)    NULL,
    emergency_contact_phone VARCHAR(20)     NULL,
    allergies               TEXT            NULL,
    medical_history         TEXT            NULL,
    registered_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    registered_by           BIGINT          NULL,

    PRIMARY KEY (id),
    INDEX idx_patients_name (first_name, last_name),
    INDEX idx_patients_phone (phone),
    INDEX idx_patients_registered_at (registered_at),
    CONSTRAINT fk_patients_registered_by FOREIGN KEY (registered_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 10. APPOINTMENTS
-- ------------------------------------------------------------
CREATE TABLE appointments (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    patient_id          BIGINT      NOT NULL,
    doctor_id           BIGINT      NOT NULL,
    booked_by           BIGINT      NOT NULL,
    appointment_date    DATE        NOT NULL,
    appointment_time    TIME        NULL COMMENT 'NULL for walk-in appointments',
    type                VARCHAR(20) NOT NULL COMMENT 'SCHEDULED, WALK_IN',
    token_number        INT         NULL COMMENT 'Auto-generated for walk-in per doctor per day',
    status              VARCHAR(20) NOT NULL DEFAULT 'BOOKED' COMMENT 'BOOKED, CHECKED_IN, IN_PROGRESS, COMPLETED, CANCELLED',
    notes               TEXT        NULL,
    created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_appointments_date (appointment_date),
    INDEX idx_appointments_doctor_date (doctor_id, appointment_date),
    INDEX idx_appointments_patient (patient_id),
    INDEX idx_appointments_status (status),
    INDEX idx_appointments_type (type),
    INDEX idx_appointments_token (doctor_id, appointment_date, type, token_number),
    CONSTRAINT fk_appointments_patient   FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_appointments_doctor    FOREIGN KEY (doctor_id)  REFERENCES doctors (id),
    CONSTRAINT fk_appointments_booked_by FOREIGN KEY (booked_by)  REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- CLINICAL TABLES
-- ============================================================

-- ------------------------------------------------------------
-- 11. DIAGNOSES
-- ------------------------------------------------------------
CREATE TABLE diagnoses (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    appointment_id  BIGINT      NOT NULL,
    doctor_id       BIGINT      NOT NULL,
    symptoms        TEXT        NOT NULL,
    diagnosis       TEXT        NOT NULL,
    clinical_notes  TEXT        NULL,
    severity        VARCHAR(20) NULL COMMENT 'MILD, MODERATE, SEVERE, CRITICAL',
    follow_up_date  DATE        NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_diagnoses_appointment (appointment_id),
    INDEX idx_diagnoses_doctor (doctor_id),
    INDEX idx_diagnoses_created_at (created_at),
    CONSTRAINT fk_diagnoses_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT fk_diagnoses_doctor      FOREIGN KEY (doctor_id)      REFERENCES doctors (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 12. MEDICINES (extends existing table)
-- ------------------------------------------------------------
CREATE TABLE medicines (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(255)    NOT NULL,
    generic_name    VARCHAR(255)    NULL,
    brand           VARCHAR(255)    NULL,
    strength        VARCHAR(50)     NULL,
    form            VARCHAR(50)     NULL COMMENT 'TABLET, CAPSULE, SYRUP, INJECTION, OINTMENT, DROPS, INHALER',
    category        VARCHAR(100)    NULL COMMENT 'Antibiotic, Painkiller, Antidiabetic, etc.',
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,

    PRIMARY KEY (id),
    INDEX idx_medicines_name (name),
    INDEX idx_medicines_generic (generic_name),
    INDEX idx_medicines_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 13. PRESCRIPTIONS
-- ------------------------------------------------------------
CREATE TABLE prescriptions (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    diagnosis_id    BIGINT          NOT NULL,
    medicine_id     BIGINT          NOT NULL,
    dosage          VARCHAR(100)    NOT NULL,
    frequency       VARCHAR(100)    NOT NULL,
    duration_days   INT             NOT NULL,
    instructions    TEXT            NULL,

    PRIMARY KEY (id),
    INDEX idx_prescriptions_diagnosis (diagnosis_id),
    INDEX idx_prescriptions_medicine (medicine_id),
    CONSTRAINT fk_prescriptions_diagnosis FOREIGN KEY (diagnosis_id) REFERENCES diagnoses (id) ON DELETE CASCADE,
    CONSTRAINT fk_prescriptions_medicine  FOREIGN KEY (medicine_id)  REFERENCES medicines (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- INVENTORY TABLES
-- ============================================================

-- ------------------------------------------------------------
-- 14. MEDICINE_INVENTORY
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
    INDEX idx_inventory_batch (batch_number),
    CONSTRAINT fk_inventory_medicine FOREIGN KEY (medicine_id) REFERENCES medicines (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- BILLING TABLES
-- ============================================================

-- ------------------------------------------------------------
-- 15. BILLINGS
-- ------------------------------------------------------------
CREATE TABLE billings (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    appointment_id  BIGINT          NULL,
    patient_id      BIGINT          NOT NULL,
    invoice_number  VARCHAR(50)     NOT NULL,
    total_amount    DECIMAL(12,2)   NOT NULL,
    discount        DECIMAL(10,2)   NOT NULL DEFAULT 0.00,
    tax             DECIMAL(10,2)   NOT NULL DEFAULT 0.00,
    net_amount      DECIMAL(12,2)   NOT NULL,
    payment_status  VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, PAID, PARTIAL, CANCELLED',
    payment_method  VARCHAR(20)     NULL COMMENT 'CASH, CARD, UPI, INSURANCE',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_billings_invoice (invoice_number),
    INDEX idx_billings_appointment (appointment_id),
    INDEX idx_billings_patient (patient_id),
    INDEX idx_billings_status (payment_status),
    INDEX idx_billings_created_at (created_at),
    CONSTRAINT fk_billings_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT fk_billings_patient     FOREIGN KEY (patient_id)     REFERENCES patients (id),
    CONSTRAINT fk_billings_created_by  FOREIGN KEY (created_by)     REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 16. BILLING_ITEMS
-- ------------------------------------------------------------
CREATE TABLE billing_items (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    billing_id  BIGINT          NOT NULL,
    description VARCHAR(255)    NOT NULL,
    amount      DECIMAL(10,2)   NOT NULL,
    quantity    INT             NOT NULL DEFAULT 1,
    item_type   VARCHAR(20)     NOT NULL COMMENT 'CONSULTATION, LAB, MEDICINE, PROCEDURE',

    PRIMARY KEY (id),
    INDEX idx_billing_items_billing (billing_id),
    INDEX idx_billing_items_type (item_type),
    CONSTRAINT fk_billing_items_billing FOREIGN KEY (billing_id) REFERENCES billings (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 2. Seed Data Script

```sql
-- ============================================================
-- SEED DATA
-- Run this AFTER the DDL above
-- ============================================================

USE hospitalsystems;

-- ------------------------------------------------------------
-- 2.1 ROLES
-- ------------------------------------------------------------
INSERT INTO roles (name) VALUES
    ('ROLE_ADMIN'),
    ('ROLE_DOCTOR'),
    ('ROLE_RECEPTIONIST');

-- ------------------------------------------------------------
-- 2.2 ADMIN USER
-- Password: admin123 (BCrypt encoded)
-- Generate your own at: https://bcrypt-generator.com/
-- ------------------------------------------------------------
INSERT INTO users (email, password, full_name, phone, is_active)
VALUES (
    'admin@curamatrix.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'System Admin',
    '9999999999',
    TRUE
);

INSERT INTO user_roles (user_id, role_id)
VALUES (
    (SELECT id FROM users WHERE email = 'admin@curamatrix.com'),
    (SELECT id FROM roles WHERE name = 'ROLE_ADMIN')
);

-- ------------------------------------------------------------
-- 2.3 DEPARTMENTS
-- ------------------------------------------------------------
INSERT INTO departments (name, description, is_active) VALUES
    ('General Medicine',    'General health consultations and primary care',    TRUE),
    ('Cardiology',          'Heart and cardiovascular system',                  TRUE),
    ('Orthopedics',         'Bones, joints, and muscles',                       TRUE),
    ('Pediatrics',          'Child healthcare and development',                 TRUE),
    ('Dermatology',         'Skin, hair, and nails',                            TRUE),
    ('ENT',                 'Ear, Nose, and Throat',                            TRUE),
    ('Ophthalmology',       'Eye care and vision',                              TRUE),
    ('Gynecology',          'Women health and reproductive system',             TRUE),
    ('Neurology',           'Brain and nervous system',                         TRUE),
    ('Dentistry',           'Dental care and oral health',                      TRUE);

-- ------------------------------------------------------------
-- 2.4 SAMPLE DOCTOR
-- Password: doctor123 (BCrypt encoded)
-- ------------------------------------------------------------
INSERT INTO users (email, password, full_name, phone, is_active)
VALUES (
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

INSERT INTO doctors (user_id, department_id, specialization, license_number, qualification, experience_years, consultation_fee)
VALUES (
    (SELECT id FROM users WHERE email = 'doctor@curamatrix.com'),
    (SELECT id FROM departments WHERE name = 'General Medicine'),
    'General Medicine',
    'MCI-GM-001',
    'MBBS, MD (General Medicine)',
    12,
    500.00
);

-- ------------------------------------------------------------
-- 2.5 SAMPLE RECEPTIONIST
-- Password: reception123 (BCrypt encoded)
-- ------------------------------------------------------------
INSERT INTO users (email, password, full_name, phone, is_active)
VALUES (
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

INSERT INTO receptionists (user_id, employee_id, shift)
VALUES (
    (SELECT id FROM users WHERE email = 'reception@curamatrix.com'),
    'REC-001',
    'MORNING'
);

-- ------------------------------------------------------------
-- 2.6 SAMPLE MEDICINES
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
-- 2.7 SAMPLE INVENTORY (for above medicines)
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

-- Low stock item (for testing low-stock alerts)
INSERT INTO medicine_inventory (medicine_id, quantity, unit_price, expiry_date, batch_number) VALUES
    (1, 3, 2.50, '2026-04-15', 'BATCH-PCM500-OLD');

-- Expiring soon item (for testing expiry alerts)
INSERT INTO medicine_inventory (medicine_id, quantity, unit_price, expiry_date, batch_number) VALUES
    (6, 50, 12.00, '2026-04-01', 'BATCH-AZITH-OLD');
```

---

## 3. Verification Queries

Run these after executing the DDL + seed data to verify everything is set up correctly:

```sql
-- ============================================================
-- VERIFICATION QUERIES
-- ============================================================

USE hospitalsystems;

-- 3.1 Check all tables were created
SELECT TABLE_NAME, TABLE_ROWS, CREATE_TIME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'hospitalsystems'
ORDER BY TABLE_NAME;

-- Expected: 14 tables
-- billing_items, billings, departments, diagnoses, doctors,
-- medicine_inventory, medicines, patients, prescriptions,
-- receptionists, roles, user_roles, users, appointments

-- 3.2 Check roles
SELECT * FROM roles;
-- Expected: 3 rows (ROLE_ADMIN, ROLE_DOCTOR, ROLE_RECEPTIONIST)

-- 3.3 Check users and their roles
SELECT u.id, u.email, u.full_name, u.is_active, r.name AS role
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id
ORDER BY u.id;
-- Expected: 3 users (admin, doctor, receptionist)

-- 3.4 Check doctor profile
SELECT d.id, u.full_name, dep.name AS department, d.specialization,
       d.license_number, d.consultation_fee
FROM doctors d
JOIN users u ON d.user_id = u.id
JOIN departments dep ON d.department_id = dep.id;
-- Expected: 1 doctor (Dr. Rajesh Kumar, General Medicine)

-- 3.5 Check receptionist profile
SELECT r.id, u.full_name, r.employee_id, r.shift
FROM receptionists r
JOIN users u ON r.user_id = u.id;
-- Expected: 1 receptionist (Neha Gupta, REC-001, MORNING)

-- 3.6 Check departments
SELECT id, name, is_active FROM departments ORDER BY id;
-- Expected: 10 departments

-- 3.7 Check medicines
SELECT id, name, strength, form, category FROM medicines ORDER BY id;
-- Expected: 25 medicines

-- 3.8 Check inventory
SELECT m.name, m.strength, mi.quantity, mi.unit_price, mi.expiry_date, mi.batch_number
FROM medicine_inventory mi
JOIN medicines m ON mi.medicine_id = m.id
ORDER BY m.name;
-- Expected: 27 inventory records (25 normal + 1 low stock + 1 expiring soon)

-- 3.9 Check low stock items (quantity < 10)
SELECT m.name, m.strength, mi.quantity, mi.batch_number
FROM medicine_inventory mi
JOIN medicines m ON mi.medicine_id = m.id
WHERE mi.quantity < 10;
-- Expected: 1 row (Paracetamol 500mg, qty 3)

-- 3.10 Check expiring soon items (within 30 days)
SELECT m.name, m.strength, mi.quantity, mi.expiry_date, mi.batch_number
FROM medicine_inventory mi
JOIN medicines m ON mi.medicine_id = m.id
WHERE mi.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 30 DAY);
-- Expected: items expiring within 30 days from today

-- 3.11 Foreign key check
SELECT
    TABLE_NAME, COLUMN_NAME, CONSTRAINT_NAME,
    REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'hospitalsystems'
  AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY TABLE_NAME, COLUMN_NAME;
-- Expected: 14 foreign key relationships
```

---

## 4. Default Login Credentials

| Role | Email | Password |
|------|-------|----------|
| Admin | `admin@curamatrix.com` | `admin123` |
| Doctor | `doctor@curamatrix.com` | `doctor123` |
| Receptionist | `reception@curamatrix.com` | `reception123` |

> **Note:** The BCrypt hashes above are samples. When the Spring Boot app starts, use the `AuthService` to create users with properly encoded passwords. Alternatively, generate BCrypt hashes at [https://bcrypt-generator.com/](https://bcrypt-generator.com/) and replace the values in the seed script.

---

## 5. Standalone SQL File

For convenience, a standalone `.sql` file is also provided at `docs/schema.sql`. You can execute it directly:

```bash
mysql -u root -p < docs/schema.sql
```

---

## 6. Table Summary

| # | Table | Description | Rows (Seed) |
|---|-------|-------------|-------------|
| 1 | `users` | Login accounts for all roles | 3 |
| 2 | `roles` | Role definitions | 3 |
| 3 | `user_roles` | User-to-role mapping (M:N) | 3 |
| 4 | `departments` | Hospital departments | 10 |
| 5 | `doctors` | Doctor profiles linked to users | 1 |
| 6 | `receptionists` | Receptionist profiles linked to users | 1 |
| 7 | `patients` | Patient records | 0 |
| 8 | `appointments` | Scheduled + walk-in appointments | 0 |
| 9 | `diagnoses` | Doctor diagnoses per appointment | 0 |
| 10 | `prescriptions` | Prescribed medicines per diagnosis | 0 |
| 11 | `medicines` | Medicine master data | 25 |
| 12 | `medicine_inventory` | Stock with batch and expiry | 27 |
| 13 | `billings` | Invoice headers | 0 |
| 14 | `billing_items` | Invoice line items | 0 |

**Total: 14 tables, ~73 seed records**

---

## 7. Index Summary

| Table | Index | Columns | Purpose |
|-------|-------|---------|---------|
| users | uk_users_email | email | Unique login |
| users | idx_users_active | is_active | Filter active users |
| users | idx_users_full_name | full_name | Search by name |
| roles | uk_roles_name | name | Unique role name |
| departments | idx_departments_active | is_active | Filter active departments |
| doctors | uk_doctors_user | user_id | One doctor per user |
| doctors | uk_doctors_license | license_number | Unique license |
| doctors | idx_doctors_department | department_id | Filter by department |
| receptionists | uk_receptionists_user | user_id | One receptionist per user |
| receptionists | uk_receptionists_employee | employee_id | Unique employee ID |
| patients | idx_patients_name | first_name, last_name | Search by name |
| patients | idx_patients_phone | phone | Search by phone |
| patients | idx_patients_registered_at | registered_at | Sort by registration date |
| appointments | idx_appointments_date | appointment_date | Filter by date |
| appointments | idx_appointments_doctor_date | doctor_id, appointment_date | Doctor's daily queue |
| appointments | idx_appointments_patient | patient_id | Patient's appointments |
| appointments | idx_appointments_status | status | Filter by status |
| appointments | idx_appointments_token | doctor_id, appointment_date, type, token_number | Walk-in token lookup |
| diagnoses | uk_diagnoses_appointment | appointment_id | One diagnosis per appointment |
| diagnoses | idx_diagnoses_doctor | doctor_id | Doctor's diagnoses |
| medicines | idx_medicines_name | name | Medicine search |
| medicines | idx_medicines_generic | generic_name | Generic name search |
| medicine_inventory | idx_inventory_medicine | medicine_id | Stock by medicine |
| medicine_inventory | idx_inventory_expiry | expiry_date | Expiry alerts |
| billings | uk_billings_invoice | invoice_number | Unique invoice |
| billings | idx_billings_patient | patient_id | Patient billing history |
| billings | idx_billings_status | payment_status | Filter by payment status |
| billings | idx_billings_created_at | created_at | Revenue reports by date |
| billing_items | idx_billing_items_billing | billing_id | Items per bill |
| billing_items | idx_billing_items_type | item_type | Revenue by type reports |

---

**End of DDL Documentation**
**CuraMatrix HSM v1.0.0**
