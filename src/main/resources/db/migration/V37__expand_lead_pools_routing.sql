ALTER TABLE lead_pools
    ADD COLUMN IF NOT EXISTS broker_selection_mode VARCHAR(32) NOT NULL DEFAULT 'ALL_BROKERS',
    ADD COLUMN IF NOT EXISTS trigger_after_inactive_days INT;

UPDATE lead_pools
SET broker_selection_mode = COALESCE(broker_selection_mode, 'ALL_BROKERS');

CREATE TABLE IF NOT EXISTS lead_pool_brokers (
    lead_pool_id UUID NOT NULL REFERENCES lead_pools(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (lead_pool_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_lead_pool_brokers_user_id ON lead_pool_brokers (user_id);
CREATE INDEX IF NOT EXISTS idx_lead_pools_inactivity ON lead_pools (tenant_id, trigger_after_inactive_days, priority);
