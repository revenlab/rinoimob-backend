-- Add slug field to properties table
ALTER TABLE properties ADD COLUMN slug VARCHAR(255);

-- Create UNIQUE index on slug per tenant (only non-deleted)
CREATE UNIQUE INDEX idx_properties_slug_tenant 
    ON properties (tenant_id, slug) 
    WHERE deleted_at IS NULL;
