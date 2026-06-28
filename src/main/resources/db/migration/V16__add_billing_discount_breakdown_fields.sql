-- Add section_discounts column to store department-wise discount breakdown
-- Format: "DEPT1:amount1;DEPT2:amount2;..." (e.g., "CARDIOLOGY:500.00;RADIOLOGY:200.00;")
ALTER TABLE billings
ADD COLUMN section_discounts VARCHAR(1000) NULL;

-- Add discount_approved_by column to track who approved the discount
ALTER TABLE billings
ADD COLUMN discount_approved_by VARCHAR(100) NULL;
