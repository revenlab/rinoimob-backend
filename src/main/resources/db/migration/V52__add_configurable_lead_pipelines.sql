CREATE TABLE lead_pipelines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uq_lead_pipelines_default_per_tenant ON lead_pipelines(tenant_id) WHERE is_default AND archived_at IS NULL;

CREATE TABLE lead_pipeline_stages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id UUID NOT NULL REFERENCES lead_pipelines(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    position INTEGER NOT NULL,
    kind VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_lead_pipeline_stage_kind CHECK (kind IN ('OPEN', 'WON', 'LOST'))
);
CREATE UNIQUE INDEX uq_lead_pipeline_stage_position ON lead_pipeline_stages(pipeline_id, position);
CREATE UNIQUE INDEX uq_lead_pipeline_terminal_stage ON lead_pipeline_stages(pipeline_id, kind) WHERE kind IN ('WON', 'LOST');

CREATE TABLE lead_pipeline_sources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id UUID NOT NULL REFERENCES lead_pipelines(id) ON DELETE CASCADE,
    source VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (pipeline_id, source)
);
CREATE UNIQUE INDEX uq_lead_pipeline_sources_tenant_source ON lead_pipeline_sources(source, pipeline_id);

ALTER TABLE leads ADD COLUMN pipeline_id UUID REFERENCES lead_pipelines(id);
ALTER TABLE leads ADD COLUMN pipeline_stage_id UUID REFERENCES lead_pipeline_stages(id);
ALTER TABLE leads ADD COLUMN duplicated_from_lead_id UUID REFERENCES leads(id) ON DELETE SET NULL;
CREATE INDEX idx_leads_pipeline_id ON leads(pipeline_id);
CREATE INDEX idx_leads_pipeline_stage_id ON leads(pipeline_stage_id);

-- Every existing tenant receives the default Vendas pipeline, its current stages, and the current lead mapping.
INSERT INTO lead_pipelines (id, tenant_id, name, is_default)
SELECT gen_random_uuid(), id, 'Vendas', TRUE FROM tenants;

INSERT INTO lead_pipeline_stages (id, pipeline_id, name, position, kind)
SELECT gen_random_uuid(), p.id, s.name, s.position, s.kind
FROM lead_pipelines p
CROSS JOIN (VALUES ('Novo', 10, 'OPEN'), ('Em contato', 20, 'OPEN'), ('Qualificado', 30, 'OPEN'), ('Ganho', 90, 'WON'), ('Perdido', 100, 'LOST')) AS s(name, position, kind)
WHERE p.is_default;

INSERT INTO lead_pipeline_sources (pipeline_id, source)
SELECT p.id, s.source FROM lead_pipelines p
CROSS JOIN (VALUES ('PORTAL'), ('PORTAL_HOME_FORM'), ('PORTAL_PROPERTY_FORM'), ('PORTAL_PROPERTY_ANNOUNCEMENT'), ('PORTAL_PROPERTY_NOT_FOUND_FORM'), ('PORTAL_WHATSAPP_HOME'), ('PORTAL_WHATSAPP_PROPERTY'), ('PORTAL_WHATSAPP_GENERIC'), ('WHATSAPP'), ('MANUAL')) AS s(source)
WHERE p.is_default;

UPDATE leads l
SET pipeline_id = p.id,
    pipeline_stage_id = st.id
FROM lead_pipelines p
JOIN lead_pipeline_stages st ON st.pipeline_id = p.id
WHERE p.tenant_id = l.tenant_id
  AND p.is_default
  AND st.kind = CASE l.status WHEN 'WON' THEN 'WON' WHEN 'LOST' THEN 'LOST' ELSE 'OPEN' END
  AND (st.kind <> 'OPEN' OR st.position = CASE l.status WHEN 'NEW' THEN 10 WHEN 'CONTACTED' THEN 20 ELSE 30 END);

ALTER TABLE leads ALTER COLUMN pipeline_id SET NOT NULL;
ALTER TABLE leads ALTER COLUMN pipeline_stage_id SET NOT NULL;
