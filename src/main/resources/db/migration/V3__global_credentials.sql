-- V3__global_credentials.sql
-- Introduce global identity: one credential (email + password) per person,
-- independent of tenant memberships.

CREATE TABLE global_credentials (
    email VARCHAR(255) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Migrate existing per-user passwords into global_credentials.
-- When the same email appears in multiple tenants, keep the earliest row's hash.
INSERT INTO global_credentials (email, password_hash, created_at)
SELECT DISTINCT ON (email) email, password_hash, created_at
FROM users
WHERE password_hash IS NOT NULL
ORDER BY email, created_at;

-- Remove per-tenant password column now that it lives in global_credentials.
ALTER TABLE users DROP COLUMN IF EXISTS password_hash;
