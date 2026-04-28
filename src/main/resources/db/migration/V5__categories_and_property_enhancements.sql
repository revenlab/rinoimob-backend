-- V5: Categories, property enhancements (reference code, floor number, condition)

-- Categories table (tenant_id NULL = global/system category)
CREATE TABLE property_categories (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    name      VARCHAR(100) NOT NULL,
    slug      VARCHAR(100) NOT NULL,
    color     VARCHAR(7),
    active    BOOLEAN NOT NULL DEFAULT TRUE,
    position  INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_category_slug_tenant UNIQUE (tenant_id, slug)
);

-- Global categories can't use the same constraint (tenant_id is NULL); enforce via partial index
CREATE UNIQUE INDEX uq_global_category_slug ON property_categories (slug) WHERE tenant_id IS NULL;

-- Junction: property ↔ category (many-to-many)
CREATE TABLE property_category_map (
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES property_categories(id) ON DELETE CASCADE,
    PRIMARY KEY (property_id, category_id)
);

-- Property enhancements
ALTER TABLE properties
    ADD COLUMN reference_code VARCHAR(50),
    ADD COLUMN floor_number   INT,
    ADD COLUMN condition      VARCHAR(30);

-- Create index for reference_code lookups per tenant
CREATE INDEX idx_properties_reference_code ON properties (tenant_id, reference_code) WHERE reference_code IS NOT NULL;

-- Seed global categories
INSERT INTO property_categories (id, tenant_id, name, slug, color, position) VALUES
    ('00000000-0000-0000-0000-000000000001', NULL, 'Lançamentos',  'lancamentos',  '#10b981', 1),
    ('00000000-0000-0000-0000-000000000002', NULL, 'Avulsos',      'avulsos',      '#6366f1', 2),
    ('00000000-0000-0000-0000-000000000003', NULL, 'Leilão',       'leilao',       '#f59e0b', 3);
