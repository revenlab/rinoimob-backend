-- V4__property_schema.sql
-- Replace Phase 0 placeholder properties table with full spec.
-- Add property_photos, floor_plans, floor_plan_photos.

DROP TABLE IF EXISTS properties CASCADE;

CREATE TABLE properties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    operation VARCHAR(20) NOT NULL,
    property_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    price NUMERIC(14,2),
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL',
    taxes NUMERIC(14,2),
    condo_fee NUMERIC(14,2),
    area_total NUMERIC(10,2),
    area_useful NUMERIC(10,2),
    bedrooms INT,
    suites INT,
    bathrooms INT,
    parking INT,
    address_street VARCHAR(255),
    address_number VARCHAR(20),
    address_complement VARCHAR(100),
    address_neighborhood VARCHAR(100),
    address_city VARCHAR(100),
    address_state VARCHAR(100),
    address_country VARCHAR(2) NOT NULL DEFAULT 'BR',
    address_zip VARCHAR(20),
    lat NUMERIC(10,7),
    lng NUMERIC(10,7),
    cover_photo_id UUID,
    attributes JSONB NOT NULL DEFAULT '{}',
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_properties_tenant_id ON properties(tenant_id);
CREATE INDEX idx_properties_status ON properties(status);
CREATE INDEX idx_properties_operation ON properties(operation);
CREATE INDEX idx_properties_property_type ON properties(property_type);
CREATE INDEX idx_properties_address_city ON properties(address_city);
CREATE INDEX idx_properties_deleted_at ON properties(deleted_at);

CREATE TABLE property_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    seaweed_fid VARCHAR(100) NOT NULL,
    url TEXT NOT NULL,
    position INT NOT NULL DEFAULT 0,
    is_cover BOOLEAN NOT NULL DEFAULT FALSE,
    alt_text VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_property_photos_property_id ON property_photos(property_id);

CREATE TABLE floor_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    area NUMERIC(10,2),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_floor_plans_property_id ON floor_plans(property_id);

CREATE TABLE floor_plan_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    floor_plan_id UUID NOT NULL REFERENCES floor_plans(id) ON DELETE CASCADE,
    seaweed_fid VARCHAR(100) NOT NULL,
    url TEXT NOT NULL,
    position INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_floor_plan_photos_floor_plan_id ON floor_plan_photos(floor_plan_id);
