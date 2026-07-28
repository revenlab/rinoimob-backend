ALTER TABLE properties
    ADD COLUMN IF NOT EXISTS is_featured BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_properties_tenant_featured_active
    ON properties (tenant_id, is_featured, status)
    WHERE deleted_at IS NULL;
