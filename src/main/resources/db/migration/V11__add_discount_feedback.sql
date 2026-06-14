-- Add discount_feedback column to billings table
ALTER TABLE billings
ADD COLUMN discount_feedback VARCHAR(500) NULL;
