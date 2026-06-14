-- Add discount_approved column to track if receptionist's discount has been approved by admin
ALTER TABLE billings ADD COLUMN discount_approved BOOLEAN DEFAULT TRUE;
