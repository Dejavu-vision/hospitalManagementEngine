-- ============================================================
-- CuraMatrix HSM - Multi-Tenant Migration Script
-- Converts single-tenant to multi-tenant architecture
-- Version: 2.0.0 (SaaS)
-- Date: 2026-03-11
-- ============================================================

USE hospitalsystems;

-- ============================================================
-- STEP 1: CREATE TENANTS TABLE
-- ============================================================

CREATE TABLE IF NOT EXISTS tenants (
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
-- STEP 2: CREATE DEFAULT TENANT (for existing data)
-- ============================================================

INSERT INTO tenants (
    tenant_key, 
    hospital_name, 
    subscription_plan, 
    subscription_start, 
    subscription_end,
    is_active,
    max_users,
    max_patients,
    contact_email,
    contact_phone,
    address
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

-- ============================================================
-- STEP 3: ADD tenant_id TO ALL TABLES
-- ============================================================

-- Users table
ALTER TABLE users 
ADD COLUMN tenant_id BIGINT NULL AFTER id;

UPDATE users SET tenant_id = @default_tenant_id WHERE tenant_id IS NULL;

ALTER TABLE users 
MODIFY COLUMN tenant_id BIGINT NOT NULL,
ADD CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
ADD INDEX idx_users_tenant (tenant_id);

-- Drop old unique constraint and add new one with tenant_id
ALTER TABLE users DROP INDEX uk_users_email;
ALTER TABLE users ADD UNIQUE KEY uk_users_email_tenant (email, tenant_id);

-- Patients table
ALTER TABLE patients 
ADD COLUMN tenant_id BIGINT NULL AFTER id;

UPDATE patients SET tenant_id = @default_tenant_id WHERE tenant_id IS NULL;

ALTER TABLE patients 
MODIFY COLUMN tenant_id BIGINT NOT NULL,
ADD CONSTRAINT fk_patients_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
ADD INDEX idx_patients_tenant (tenant_id);

-- Appointments table
ALTER TABLE appointments 
ADD COLUMN tenant_id BIGINT NULL AFTER id;

UPDATE appointments SET tenant_id = @default_tenant_id WHERE tenant_id IS NULL;

ALTER TABLE appointments 
MODIFY COLUMN tenant_id BIGINT NOT NULL,
ADD CONSTRAINT fk_appointments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
ADD INDEX idx_appointments_tenant (tenant_id);

-- Diagnoses table
ALTER TABLE diagnoses 
ADD COLUMN tenant_id BIGINT NULL AFTER id;

UPDATE diagnoses SET tenant_id = @default_tenant_id WHERE tenant_id IS NULL;

ALTER TABLE diagnoses 
MODIFY COLUMN tenant_id BIGINT NOT NULL,
ADD CONSTRAINT fk_diagnoses_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
ADD INDEX idx_diagnoses_tenant (tenant_id);

-- Prescriptions table
ALTER TABLE prescriptions 
ADD COLUMN tenant_id BIGINT NULL AFTER id;

UPDATE prescriptions SET tenant_id = @default_tenant_id WHERE tenant_id IS NULL;

ALTER TABLE prescriptions 
MODIFY COLUMN tenant_id BIGINT NOT NULL,
ADD CONSTRAINT fk_prescriptions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
ADD INDEX idx_prescriptions_tenant (tenant_id);

-- Billings table
ALTER TABLE billings 
ADD COLUMN tenant_id BIGINT NULL AFTER id;

UPDATE billings SET tenant_id = @default_tenant_id WHERE tenant_id IS NULL;

ALTER TABLE billings 
MODIFY COLUMN tenant_id BIGINT NOT NULL,
ADD CONSTRAINT fk_billings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
ADD INDEX idx_billings_tenant (tenant_id);

-- Billing Items table
ALTER TABLE billing_items 
ADD COLUMN tenant_id BIGINT NULL AFTER id;

UPDATE billing_items SET tenant_id = @default_tenant_id WHERE tenant_id IS NULL;

ALTER TABLE billing_items 
MODIFY COLUMN tenant_id BIGINT NOT NULL,
ADD CONSTRAINT fk_billing_items_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
ADD INDEX idx_billing_items_tenant (tenant_id);

-- Doctors table
ALTER TABLE doctors 
ADD COLUMN tenant_id BIGINT NULL AFTER id;

UPDATE doctors SET tenant_id = @default_tenant_id WHERE tenant_id IS NULL;

ALTER TABLE doctors 
MODIFY COLUMN tenant_id BIGINT NOT NULL,
ADD CONSTRAINT fk_doctors_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
ADD INDEX idx_doctors_tenant (tenant_id);

-- Receptionists table
ALTER TABLE receptionists 
ADD COLUMN tenant_id BIGINT NULL AFTER id;

UPDATE receptionists SET tenant_id = @default_tenant_id WHERE tenant_id IS NULL;

ALTER TABLE receptionists 
MODIFY COLUMN tenant_id BIGINT NOT NULL,
ADD CONSTRAINT fk_receptionists_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
ADD INDEX idx_receptionists_tenant (tenant_id);

-- Departments table (shared across tenants, but can be tenant-specific)
ALTER TABLE departments 
ADD COLUMN tenant_id BIGINT NULL AFTER id;

UPDATE departments SET tenant_id = @default_tenant_id WHERE tenant_id IS NULL;

ALTER TABLE departments 
MODIFY COLUMN tenant_id BIGINT NOT NULL,
ADD CONSTRAINT fk_departments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
ADD INDEX idx_departments_tenant (tenant_id);

-- ============================================================
-- STEP 4: ADD SUPER_ADMIN ROLE
-- ============================================================

INSERT INTO roles (name) VALUES ('ROLE_SUPER_ADMIN')
ON DUPLICATE KEY UPDATE name = name;

-- ============================================================
-- STEP 5: CREATE SUPER ADMIN USER (Platform Level)
-- ============================================================

-- Note: This user has NO tenant_id (platform-level access)
INSERT INTO users (email, password, full_name, phone, is_active, tenant_id)
VALUES (
    'superadmin@curamatrix.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', -- admin123
    'Super Administrator',
    '9999999999',
    TRUE,
    @default_tenant_id  -- Temporary, will be handled specially in code
) ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_roles (user_id, role_id)
SELECT 
    (SELECT id FROM users WHERE email = 'superadmin@curamatrix.com'),
    (SELECT id FROM roles WHERE name = 'ROLE_SUPER_ADMIN')
WHERE NOT EXISTS (
    SELECT 1 FROM user_roles ur
    WHERE ur.user_id = (SELECT id FROM users WHERE email = 'superadmin@curamatrix.com')
    AND ur.role_id = (SELECT id FROM roles WHERE name = 'ROLE_SUPER_ADMIN')
);

-- ============================================================
-- STEP 6: CREATE ADDITIONAL TABLES FOR SAAS
-- ============================================================

-- Tenant Subscriptions (for billing history)
CREATE TABLE IF NOT EXISTS tenant_subscriptions (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id       BIGINT NOT NULL,
    plan_name       VARCHAR(50) NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    billing_cycle   VARCHAR(20) NOT NULL, -- MONTHLY, YEARLY
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    status          VARCHAR(20) NOT NULL, -- ACTIVE, EXPIRED, CANCELLED
    payment_method  VARCHAR(50),
    transaction_id  VARCHAR(255),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    INDEX idx_subscriptions_tenant (tenant_id),
    INDEX idx_subscriptions_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tenant Usage Statistics
CREATE TABLE IF NOT EXISTS tenant_usage_stats (
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

-- Tenant Audit Log
CREATE TABLE IF NOT EXISTS tenant_audit_log (
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
-- STEP 7: CREATE COMPOSITE INDEXES FOR PERFORMANCE
-- ============================================================

-- Optimize queries that filter by tenant_id
CREATE INDEX idx_users_tenant_email ON users(tenant_id, email);
CREATE INDEX idx_patients_tenant_phone ON patients(tenant_id, phone);
CREATE INDEX idx_appointments_tenant_date ON appointments(tenant_id, appointment_date);
CREATE INDEX idx_appointments_tenant_doctor ON appointments(tenant_id, doctor_id, appointment_date);

-- ============================================================
-- VERIFICATION QUERIES
-- ============================================================

-- Check tenants
SELECT * FROM tenants;

-- Check super admin
SELECT u.id, u.email, u.full_name, r.name AS role
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id
WHERE u.email = 'superadmin@curamatrix.com';

-- Check all tables have tenant_id
SELECT 
    TABLE_NAME,
    COLUMN_NAME,
    DATA_TYPE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'hospitalsystems'
AND COLUMN_NAME = 'tenant_id'
ORDER BY TABLE_NAME;

-- Expected: 10+ tables with tenant_id column

-- ============================================================
-- DONE! Multi-tenant migration complete
-- ============================================================

SELECT 'Multi-tenant migration completed successfully!' AS status;
