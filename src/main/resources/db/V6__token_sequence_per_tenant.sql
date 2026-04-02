-- Change walk_in_token_sequence to be per-tenant per-day (not per-doctor)
-- Tokens are now unique across ALL doctors for the day

-- Drop old doctor-scoped unique constraint and doctor_id column
ALTER TABLE walk_in_token_sequence
    DROP FOREIGN KEY fk_wts_doctor,
    DROP INDEX uq_token_seq,
    DROP COLUMN doctor_id;

-- Add new tenant-scoped unique constraint
ALTER TABLE walk_in_token_sequence
    ADD CONSTRAINT uq_token_seq_tenant UNIQUE (appointment_date, tenant_id);
