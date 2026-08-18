ALTER TABLE tenant_subscriptions
    ADD COLUMN IF NOT EXISTS past_due_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_tenant_subscriptions_past_due
    ON tenant_subscriptions (status, past_due_at);
