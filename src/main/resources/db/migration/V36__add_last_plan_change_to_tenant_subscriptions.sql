ALTER TABLE tenant_subscriptions
    ADD COLUMN IF NOT EXISTS last_plan_change_at TIMESTAMP;

UPDATE tenant_subscriptions
SET last_plan_change_at = COALESCE(last_plan_change_at, started_at, current_period_start, created_at)
WHERE last_plan_change_at IS NULL;
