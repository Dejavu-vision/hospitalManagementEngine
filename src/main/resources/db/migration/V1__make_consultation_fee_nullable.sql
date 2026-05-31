-- Allow consultation_fee to be NULL so doctors can use the department catalog rate
ALTER TABLE doctors MODIFY COLUMN consultation_fee DECIMAL(38,2) NULL;
