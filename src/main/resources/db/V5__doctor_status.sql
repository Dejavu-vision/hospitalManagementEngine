-- Extend doctor_availability with real-time status and timing fields
ALTER TABLE doctor_availability
    ADD COLUMN status       VARCHAR(20)  NOT NULL DEFAULT 'ON_DUTY',
    ADD COLUMN status_note  VARCHAR(255) NULL,
    ADD COLUMN available_from TIME       NULL,
    ADD COLUMN duty_start   TIME         NULL,
    ADD COLUMN duty_end     TIME         NULL;
