-- Add discount column to billing_items table to support row-wise discounts
ALTER TABLE billing_items
ADD COLUMN discount DECIMAL(10,2) NOT NULL DEFAULT 0.00;
