-- Add full_name and phone_number to addresses table
ALTER TABLE addresses
    ADD COLUMN IF NOT EXISTS full_name   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS phone_number VARCHAR(30);
