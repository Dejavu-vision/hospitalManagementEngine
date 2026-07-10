# CuraMatrix HSM Data Migration Schema Guide

This guide is designed for database administrators and software engineers performing data migrations or setting up new databases for the CuraMatrix HSM multi-tenant SaaS platform. 

Since the project uses Hibernate Auto Schema Evolution (`hibernate.ddl-auto: update`), many tables do not have manual `CREATE TABLE` scripts in the codebase. This guide consolidates all 50 database tables from the JPA entities, defines their topological load order to avoid foreign key constraint violations, and maps their schemas in a migration-friendly format.

---

## 1. Migration Order of Insertion (Topological Sort)

When migrating data, records must be inserted into tables in a specific sequence to satisfy foreign key dependencies. 

```
[Phase 1: Global Lookups] -> [Phase 2: Org & Staff] -> [Phase 3: Clinical Setup] -> [Phase 4: Patients] -> [Phase 5: Admissions & Transactions] -> [Phase 6: Lab & Billing]
```

### Topological Insertion Sequence:
1.  `tenants` (Base SaaS Tenants / Hospitals)
2.  `roles` (Global security roles)
3.  `ui_pages` (UI View pages routing registry)
4.  `medicines` (Global drug catalog)
5.  `blocked_tokens` (JWT blocklist)
6.  `users` (Core accounts, linked to `tenants`)
7.  `user_roles` (Many-to-many join for `users` and `roles`)
8.  `user_pages` (User-specific UI route overrides)
9.  `role_pages` (Role-specific UI route mappings)
10. `user_access_audit` (Logs page permission changes)
11. `departments` (Hospital clinical departments)
12. `doctors` (Doctor clinical records extending `users`)
13. `doctor_availability` (Doctor scheduling calendars)
14. `doctor_status_log` (Doctor check-in/out tracking logs)
15. `receptionists` (Front-desk personnel records extending `users`)
16. `wards` (Hospital in-patient ward rooms grouping)
17. `rooms` (In-patient rooms scoped to wards)
18. `beds` (Physical beds scoped to rooms)
19. `lab_services` (Global/tenant lab test catalog pricing)
20. `hospital_services` (Hospital consultations, OPD, and miscellaneous clinical services)
21. `service_pricing_tiers` (Configurable service rates depending on criteria)
22. `payer_master` (Insurance companies or payment processors)
23. `walk_in_token_sequence` (Generates token numbers for OPD walk-ins)
24. `employee_id_sequence` (Generates sequential employee IDs)
25. `ipd_admission_sequence` (Generates sequential admission numbers)
26. `patients` (Patient profiles)
27. `patient_registrations` (OPD case paper registration records)
28. `patient_financial_account` (Tracks deposits and ledger balances of patients)
29. `insurance_policies` (Active insurance coverages for patients)
30. `appointments` (OPD visits booking)
31. `appointment_status_log` (State changes for appointments)
32. `ipd_vital_signs` (Physiological measurements logged for patients)
33. `financial_blocks` (Holds/blocks placed on delinquent patient accounts)
34. `diagnoses` (Doctor clinical findings from appointments)
35. `prescriptions` (Medicines prescribed to patient diagnoses)
36. `medicine_inventory` (Pharmacy physical stock)
37. `ipd_admissions` (In-patient hospital admission records)
38. `ipd_daily_progress_notes` (Daily checks logged by attending doctors)
39. `bed_allocations` (History of room/bed assignments)
40. `pre_auth_requests` (Insurance pre-authorizations for admissions)
41. `pre_auth_coverage_items` (Individual medical items covered by pre-auth)
42. `lab_prescriptions` (Lab test prescriptions written by doctors)
43. `lab_tests` (Prescribed laboratory test orders)
44. `lab_results` (Results metrics entered by laboratory techs)
45. `lab_documents` (PDFs or files uploaded for lab reports)
46. `lab_test_status_log` (State changes for lab orders)
47. `billings` (OPD/IPD invoices generated)
48. `billing_items` (Individual line items in an invoice)
49. `bill_insurance_splits` (Copay and insurer share mappings)
50. `payments` (Receipts generated for invoice payments)
51. `payment_plans` (Installment plan mapping for large balances)
52. `bill_allocations` (Allocation mapping between payments and invoices)

---

## 2. Table Column Schema Mappings

Below is the column definition schema for core transactional tables mapped from Java JPA entities.

### Table: `tenants`
Stores independent SaaS hospital client boundaries.
*   **`id`**: `BIGINT` (Primary Key, Auto-Increment)
*   **`tenant_key`**: `VARCHAR(100)` (Unique, Not Null) - e.g., `"mumbai-apollo"`
*   **`hospital_name`**: `VARCHAR(255)` (Not Null)
*   **`subscription_plan`**: `VARCHAR(50)` (Not Null) - `BASIC`, `PRO`, `ENTERPRISE`
*   **`subscription_start`**: `DATE` (Not Null)
*   **`subscription_end`**: `DATE` (Not Null)
*   **`is_active`**: `BOOLEAN` (Default `true`)
*   **`max_users`**: `INT` (Default `50`)
*   **`max_patients`**: `INT` (Default `10000`)
*   **`contact_email`**: `VARCHAR(255)` (Not Null)
*   **`contact_phone`**: `VARCHAR(20)`
*   **`address`**: `TEXT`
*   **`logo`**: `VARCHAR(500)`
*   **`settings`**: `JSON` (System configurations override)
*   **`created_at`**: `TIMESTAMP` (Default `CURRENT_TIMESTAMP`)

### Table: `users`
Accounts assigned to specific tenants.
*   **`id`**: `BIGINT` (Primary Key, Auto-Increment)
*   **`tenant_id`**: `BIGINT` (Foreign Key -> `tenants.id`, Not Null)
*   **`email`**: `VARCHAR(255)` (Unique, Not Null) - *Must be lowcase/pre-hashed or AES-256 encrypted depending on your configuration.*
*   **`password`**: `VARCHAR(255)` (Not Null) - *BCrypt Hashed password.*
*   **`full_name`**: `VARCHAR(255)` (Not Null)
*   **`employee_id`**: `VARCHAR(20)` (Unique per tenant, Nullable)
*   **`phone`**: `VARCHAR(255)`
*   **`is_active`**: `BOOLEAN` (Default `true`)
*   **`created_at`**: `TIMESTAMP` (Default `CURRENT_TIMESTAMP`)

### Table: `doctors`
Additional clinical info mapped directly to a user account.
*   **`id`**: `BIGINT` (Primary Key, Auto-Increment)
*   **`user_id`**: `BIGINT` (Foreign Key -> `users.id`, Unique, Not Null)
*   **`department_id`**: `BIGINT` (Foreign Key -> `departments.id`, Nullable)
*   **`license_number`**: `VARCHAR(255)` (Unique, Not Null)
*   **`qualification`**: `VARCHAR(255)` - e.g., `"MBBS, MD"`
*   **`experience_years`**: `INT`
*   **`consultation_fee`**: `DECIMAL(10, 2)` (Not Null)

### Table: `patients`
Patient medical charts scoped to a tenant.
*   **`id`**: `BIGINT` (Primary Key, Auto-Increment)
*   **`tenant_id`**: `BIGINT` (Foreign Key -> `tenants.id`, Not Null)
*   **`patient_code`**: `VARCHAR(255)` (Unique, Not Null) - e.g., `"PT-00021"`
*   **`first_name`**: `VARCHAR(255)` (Not Null)
*   **`last_name`**: `VARCHAR(255)`
*   **`date_of_birth`**: `DATE`
*   **`gender`**: `VARCHAR(50)` - `MALE`, `FEMALE`, `OTHER`
*   **`phone`**: `VARCHAR(255)` (Not Null)
*   **`email`**: `VARCHAR(255)`
*   **`address`**: `TEXT`
*   **`blood_group`**: `VARCHAR(50)`
*   **`emergency_contact_name`**: `VARCHAR(255)`
*   **`emergency_contact_phone`**: `VARCHAR(255)`
*   **`guardian_name`**: `VARCHAR(255)`
*   **`allergies`**: `TEXT`
*   **`medical_history`**: `TEXT`
*   **`registered_at`**: `TIMESTAMP`
*   **`checked_in`**: `BOOLEAN` (Default `false`)
*   **`checked_out`**: `BOOLEAN` (Default `false`)
*   **`registered_by`**: `BIGINT` (Foreign Key -> `users.id`, Nullable)

### Table: `appointments`
Outpatient bookings.
*   **`id`**: `BIGINT` (Primary Key, Auto-Increment)
*   **`tenant_id`**: `BIGINT` (Foreign Key -> `tenants.id`, Not Null)
*   **`patient_id`**: `BIGINT` (Foreign Key -> `patients.id`, Not Null)
*   **`doctor_id`**: `BIGINT` (Foreign Key -> `doctors.id`, Not Null)
*   **`booked_by`**: `BIGINT` (Foreign Key -> `users.id`, Not Null)
*   **`appointment_date`**: `DATE` (Not Null)
*   **`appointment_time`**: `TIME`
*   **`type`**: `VARCHAR(50)` - `SCHEDULED`, `WALK_IN`
*   **`token_number`**: `INT` (OPD Daily sequence queue token)
*   **`status`**: `VARCHAR(50)` - `BOOKED`, `CHECKED_IN`, `IN_CONSULTATION`, `COMPLETED`, `CANCELLED`
*   **`notes`**: `TEXT`
*   **`created_at`**: `TIMESTAMP`

### Table: `ipd_admissions`
Attending inpatient stays.
*   **`id`**: `BIGINT` (Primary Key, Auto-Increment)
*   **`tenant_id`**: `BIGINT` (Foreign Key -> `tenants.id`, Not Null)
*   **`admission_number`**: `VARCHAR(255)` (Unique per tenant, Not Null)
*   **`patient_id`**: `BIGINT` (Foreign Key -> `patients.id`, Not Null)
*   **`primary_doctor_id`**: `BIGINT` (Foreign Key -> `doctors.id`, Not Null)
*   **`opd_appointment_id`**: `BIGINT` (Foreign Key -> `appointments.id`, Nullable)
*   **`admission_type`**: `VARCHAR(50)` (Not Null) - `EMERGENCY`, `ROUTINE`, `TRANSFER`
*   **`status`**: `VARCHAR(50)` (Not Null) - `ADMITTED`, `DISCHARGED`, `CANCELLED`
*   **`admission_time`**: `DATETIME` (Not Null)
*   **`expected_discharge_time`**: `DATETIME`
*   **`actual_discharge_time`**: `DATETIME`
*   **`discharge_summary`**: `TEXT`
*   **`admission_notes`**: `TEXT`
*   **`deposit_amount`**: `DECIMAL(10, 2)`
*   **`payment_method`**: `VARCHAR(50)`
*   **`pre_auth_id`**: `BIGINT`
*   **`discharge_cleared`**: `BOOLEAN` (Default `false`)
*   **`invoice_generated`**: `BOOLEAN` (Default `false`)

---

## 3. Data Migration Script Blueprint

Below is a template SQL sequence demonstrating standard practices for high-speed migrations.

```sql
-- ---------------------------------------------------------------------
-- DATA MIGRATION SCRIPTS FOR CURAMATRIX HSM
-- ---------------------------------------------------------------------

-- Step 1: Temporarily disable key checks to maximize performance and skip constraints
SET UNIQUE_CHECKS = 0;
SET FOREIGN_KEY_CHECKS = 0;
SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";

-- Step 2: Clear old system data (if performing a fresh rebuild)
TRUNCATE TABLE bill_allocations;
TRUNCATE TABLE billing_items;
TRUNCATE TABLE billings;
TRUNCATE TABLE ipd_admissions;
TRUNCATE TABLE appointments;
TRUNCATE TABLE patients;
TRUNCATE TABLE doctors;
TRUNCATE TABLE users;
TRUNCATE TABLE tenants;

-- Step 3: Insert Base Tenant
INSERT INTO tenants (id, tenant_key, hospital_name, subscription_plan, subscription_start, subscription_end, is_active)
VALUES (1, 'default-hospital', 'CuraMatrix Diagnostic Center', 'ENTERPRISE', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), 1);

-- Step 4: Insert Roles
INSERT INTO roles (id, name) VALUES 
(1, 'ROLE_SUPER_ADMIN'),
(2, 'ROLE_ADMIN'),
(3, 'ROLE_DOCTOR'),
(4, 'ROLE_RECEPTIONIST');

-- Step 5: Insert Admin User (Password: admin123 hashed with BCrypt)
INSERT INTO users (id, tenant_id, email, password, full_name, employee_id, is_active, created_at)
VALUES (1, 1, 'admin@curamatrix.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Platform Administrator', 'EMP-001', 1, NOW());

-- Link user to ROLE_ADMIN
INSERT INTO user_roles (user_id, role_id) VALUES (1, 2);

-- Step 6: Restore key checks
SET FOREIGN_KEY_CHECKS = 1;
SET UNIQUE_CHECKS = 1;
```

---

## 4. Migration Best Practices

1.  **Strict Transaction Boundaries**: Perform migrations in small batches (e.g., 500 records per transaction) to prevent InnoDB transactional locks from filling the transaction logs.
2.  **Date-Time ISO Formatting**: Ensure all local date-time inputs follow the standard ISO format (`YYYY-MM-DD HH:MM:SS`) to prevent database parsing discrepancies.
3.  **Application Layer Encryption**: If security mechanisms are enabled at the service layer, make sure data in columns like `users.email` is pre-encrypted using the configured AES-256 key, or mapped hashes are loaded into `users.email_hash`.
