CREATE TABLE tenant_property_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    label VARCHAR(100) NOT NULL,
    position INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_tenant_property_types_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_tenant_property_types_tenant_id ON tenant_property_types(tenant_id);
CREATE INDEX idx_tenant_property_types_active ON tenant_property_types(active);

INSERT INTO tenant_property_types (tenant_id, code, label, position, active)
SELECT t.id, v.code, v.label, v.position, TRUE
FROM tenants t
CROSS JOIN (
    VALUES
        ('HOUSE', 'Casa', 10),
        ('APARTMENT', 'Apartamento', 20),
        ('LAND', 'Terreno', 30),
        ('COMMERCIAL', 'Comercial', 40),
        ('RURAL', 'Rural', 50)
) AS v(code, label, position)
ON CONFLICT (tenant_id, code) DO NOTHING;
