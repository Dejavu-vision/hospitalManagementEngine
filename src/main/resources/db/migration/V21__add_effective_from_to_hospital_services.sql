-- Add effective_from column to hospital_services table to support pricing validity dates
ALTER TABLE hospital_services
ADD COLUMN effective_from DATE NOT NULL DEFAULT '2026-01-01';
