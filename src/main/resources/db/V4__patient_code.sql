-- Add patient_code column for human-readable unique patient identifier (e.g. PAT-2024-00001)
ALTER TABLE patients
    ADD COLUMN patient_code VARCHAR(20) NULL UNIQUE AFTER id;

-- Back-fill existing patients with a generated code
-- Format: PAT-{YEAR}-{zero-padded id}
UPDATE patients
SET patient_code = CONCAT('PAT-', YEAR(registered_at), '-', LPAD(id, 5, '0'))
WHERE patient_code IS NULL;

-- Make it non-nullable after back-fill
ALTER TABLE patients
    MODIFY COLUMN patient_code VARCHAR(20) NOT NULL;

-- Index for fast search by patient_code
CREATE INDEX idx_patients_code ON patients (patient_code);
