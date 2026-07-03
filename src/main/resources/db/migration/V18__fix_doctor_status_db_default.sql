-- Step 1: Expand the ENUM definition to allow temporary migration of values
ALTER TABLE doctor_availability MODIFY COLUMN status ENUM(
    'IN_CONSULTATION', 'IN_SURGERY', 'OFF_DUTY', 'ON_BREAK', 'ON_DUTY',
    'AVAILABLE', 'AWAY', 'IN_EMERGENCY', 'IN_PROCEDURE', 'OFFLINE'
) NOT NULL;

-- Step 2: Migrate data from old status names to new ones
UPDATE doctor_availability SET status = 'AVAILABLE' WHERE status = 'ON_DUTY';
UPDATE doctor_availability SET status = 'OFFLINE' WHERE status = 'OFF_DUTY';
UPDATE doctor_availability SET status = 'AWAY' WHERE status = 'ON_BREAK';
UPDATE doctor_availability SET status = 'IN_PROCEDURE' WHERE status = 'IN_SURGERY';
UPDATE doctor_availability SET status = 'AVAILABLE' WHERE status = 'IN_CONSULTATION';

-- Step 3: Convert the column to VARCHAR(20) and set correct default
ALTER TABLE doctor_availability MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE';
