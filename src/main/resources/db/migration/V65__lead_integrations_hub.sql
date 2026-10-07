ALTER TABLE billing_plans
    ADD COLUMN IF NOT EXISTS integrations_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE tenant_billing_profiles
    ADD COLUMN IF NOT EXISTS integrations_enabled BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE billing_plans
SET integrations_enabled = code IN ('STARTER', 'PRIME', 'ULTIMATE');

UPDATE tenant_billing_profiles profile
SET integrations_enabled = plan.integrations_enabled
FROM billing_plans plan
WHERE plan.id = profile.billing_plan_id;

CREATE TABLE integration_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'CONNECTING',
    external_account_id VARCHAR(160),
    external_account_name VARCHAR(200),
    encrypted_credentials TEXT,
    inbound_key VARCHAR(80) NOT NULL UNIQUE,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_received_at TIMESTAMP,
    last_processed_at TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_integration_provider CHECK (provider IN ('META_LEAD_ADS', 'GRUPO_OLX')),
    CONSTRAINT chk_integration_status CHECK (status IN ('CONNECTING', 'ACTIVE', 'PAUSED', 'EXPIRED', 'ERROR'))
);

CREATE INDEX idx_integration_connections_tenant
    ON integration_connections(tenant_id, provider);
CREATE UNIQUE INDEX uq_integration_connection_external_account
    ON integration_connections(tenant_id, provider, external_account_id)
    WHERE external_account_id IS NOT NULL;

CREATE TABLE integration_routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    connection_id UUID NOT NULL REFERENCES integration_connections(id) ON DELETE CASCADE,
    external_resource_id VARCHAR(160) NOT NULL,
    external_resource_name VARCHAR(200),
    pipeline_id UUID NOT NULL REFERENCES lead_pipelines(id),
    pool_id UUID REFERENCES lead_pools(id) ON DELETE SET NULL,
    assigned_to UUID REFERENCES users(id) ON DELETE SET NULL,
    default_property_id UUID REFERENCES properties(id) ON DELETE SET NULL,
    field_mapping JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(connection_id, external_resource_id),
    CONSTRAINT chk_integration_route_assignment CHECK (pool_id IS NULL OR assigned_to IS NULL)
);

CREATE INDEX idx_integration_routes_tenant
    ON integration_routes(tenant_id, connection_id);

CREATE TABLE integration_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    connection_id UUID NOT NULL REFERENCES integration_connections(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    external_event_id VARCHAR(200) NOT NULL,
    external_resource_id VARCHAR(160),
    event_type VARCHAR(80) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    encrypted_payload TEXT NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_error TEXT,
    lead_id UUID REFERENCES leads(id) ON DELETE SET NULL,
    provider_created_at TIMESTAMP,
    received_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(connection_id, external_event_id),
    CONSTRAINT chk_integration_event_status CHECK (status IN ('RECEIVED', 'PROCESSING', 'PROCESSED', 'FAILED', 'BLOCKED_QUOTA', 'IGNORED'))
);

CREATE INDEX idx_integration_events_pending
    ON integration_events(status, next_attempt_at, received_at);
CREATE INDEX idx_integration_events_tenant
    ON integration_events(tenant_id, received_at DESC);

CREATE TABLE lead_acquisitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    lead_id UUID NOT NULL UNIQUE REFERENCES leads(id) ON DELETE CASCADE,
    connection_id UUID NOT NULL REFERENCES integration_connections(id) ON DELETE RESTRICT,
    provider VARCHAR(30) NOT NULL,
    external_lead_id VARCHAR(200) NOT NULL,
    external_form_id VARCHAR(160),
    external_listing_id VARCHAR(160),
    campaign_name VARCHAR(255),
    ad_name VARCHAR(255),
    property_match_status VARCHAR(30) NOT NULL DEFAULT 'NOT_APPLICABLE',
    provider_created_at TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_property_match_status CHECK (property_match_status IN ('MATCHED', 'NOT_FOUND', 'NOT_APPLICABLE'))
);

CREATE INDEX idx_lead_acquisitions_tenant
    ON lead_acquisitions(tenant_id, created_at DESC);
