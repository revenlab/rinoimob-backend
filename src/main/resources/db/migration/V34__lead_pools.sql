-- Create lead_pools table and add pool_id to leads
CREATE TABLE IF NOT EXISTS lead_pools (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    name varchar(120) NOT NULL,
    description text,
    created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE leads
    ADD COLUMN IF NOT EXISTS pool_id uuid;

-- Optional FK — keep nullable to avoid migration issues on existing rows
ALTER TABLE leads
    ADD CONSTRAINT IF NOT EXISTS fk_leads_pool
    FOREIGN KEY (pool_id) REFERENCES lead_pools(id);

CREATE INDEX IF NOT EXISTS idx_lead_pools_tenant_id ON lead_pools(tenant_id);
CREATE INDEX IF NOT EXISTS idx_leads_pool_id ON leads(pool_id);
