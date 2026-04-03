-- Add vitals and investigations columns to diagnoses table
ALTER TABLE diagnoses
    ADD COLUMN temperature    VARCHAR(20)  NULL AFTER follow_up_date,
    ADD COLUMN blood_pressure VARCHAR(20)  NULL AFTER temperature,
    ADD COLUMN weight         VARCHAR(20)  NULL AFTER blood_pressure,
    ADD COLUMN investigations TEXT         NULL AFTER weight;
