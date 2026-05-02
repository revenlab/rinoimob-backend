-- V10__add_min_valid_token_issued_at_columns.sql
-- Add token invalidation tracking for both tenant-level and user-level token revocation

ALTER TABLE users
ADD COLUMN min_valid_token_issued_at BIGINT;

ALTER TABLE tenants
ADD COLUMN min_valid_token_issued_at BIGINT;

-- Add index for faster token validation queries
CREATE INDEX idx_users_min_valid_token_issued_at ON users(tenant_id, min_valid_token_issued_at);
CREATE INDEX idx_tenants_min_valid_token_issued_at ON tenants(min_valid_token_issued_at);
