-- V16__add_phone_to_users.sql
-- Add phone column to users table

ALTER TABLE users ADD COLUMN phone VARCHAR(20);

-- Add index for phone lookups (optional, but useful for contact management)
CREATE INDEX idx_users_phone ON users(tenant_id, phone);
