ALTER TABLE users
    ADD COLUMN IF NOT EXISTS public_slug VARCHAR(120),
    ADD COLUMN IF NOT EXISTS public_bio TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_tenant_public_slug
    ON users (tenant_id, public_slug)
    WHERE public_slug IS NOT NULL;
