-- Add signature column and ensure logo is TEXT to support base64 images and URLs
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS signature TEXT;
ALTER TABLE tenants ALTER COLUMN logo TYPE TEXT;
