-- Create hospital_services because this table is required by HospitalService
-- and billing_items.hospital_service_id, but is missing from older databases.

CREATE TABLE IF NOT EXISTS hospital_services (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    service_code VARCHAR(100) NOT NULL,
    price DECIMAL(38,2) NOT NULL,
    item_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT DEFAULT NULL,
    validity_period_days INT DEFAULT NULL,
    is_insurance_payable BOOLEAN DEFAULT TRUE,
    department_id BIGINT DEFAULT NULL,
    insurance_rate DECIMAL(38,2) DEFAULT NULL,
    gst_percentage DECIMAL(38,2) NOT NULL DEFAULT 0,
    effective_from DATE NOT NULL DEFAULT '2026-01-01',

    PRIMARY KEY (id),

    UNIQUE KEY uk_service_code_tenant (
        service_code,
        tenant_id
    ),

    KEY idx_hospital_services_tenant (
        tenant_id
    ),

    KEY idx_hospital_services_department (
        department_id
    ),

    KEY idx_hospital_services_item_type (
        item_type
    ),

    CONSTRAINT fk_hospital_services_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES tenants (id),

    CONSTRAINT fk_hospital_services_department
        FOREIGN KEY (department_id)
        REFERENCES departments (id)
);
