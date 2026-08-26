ALTER TABLE tenant_billing_profiles
    ADD COLUMN IF NOT EXISTS provider_customer_id VARCHAR(100);

UPDATE tenant_billing_profiles profile
SET provider_customer_id = subscription.provider_customer_id
FROM tenant_subscriptions subscription
WHERE profile.tenant_id = subscription.tenant_id
  AND profile.provider_customer_id IS NULL
  AND subscription.provider_customer_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_tenant_billing_profiles_provider_customer
    ON tenant_billing_profiles (provider_customer_id)
    WHERE provider_customer_id IS NOT NULL;

ALTER TABLE tenant_subscriptions
    ADD COLUMN IF NOT EXISTS access_restricted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS suspension_reason VARCHAR(50),
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS tenant_subscription_changes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    source_plan_id UUID NOT NULL REFERENCES billing_plans(id),
    target_plan_id UUID NOT NULL REFERENCES billing_plans(id),
    status VARCHAR(30) NOT NULL,
    external_reference VARCHAR(200) NOT NULL UNIQUE,
    provider_checkout_id VARCHAR(100),
    provider_checkout_url VARCHAR(500),
    previous_provider_subscription_id VARCHAR(100),
    new_provider_subscription_id VARCHAR(100),
    requested_by_user_id UUID,
    effective_at TIMESTAMP,
    expires_at TIMESTAMP,
    paid_at TIMESTAMP,
    applied_at TIMESTAMP,
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tenant_subscription_changes_checkout
    ON tenant_subscription_changes (provider_checkout_id)
    WHERE provider_checkout_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_tenant_subscription_changes_open
    ON tenant_subscription_changes (tenant_id)
    WHERE status IN ('PENDING', 'PAID', 'SCHEDULED');
CREATE INDEX IF NOT EXISTS idx_tenant_subscription_changes_tenant_created
    ON tenant_subscription_changes (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tenant_subscription_changes_scheduled
    ON tenant_subscription_changes (status, effective_at);

CREATE TABLE IF NOT EXISTS tenant_billing_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    provider_payment_id VARCHAR(100) NOT NULL UNIQUE,
    provider_subscription_id VARCHAR(100),
    provider_checkout_id VARCHAR(100),
    provider_customer_id VARCHAR(100),
    external_reference VARCHAR(200),
    status VARCHAR(40) NOT NULL,
    billing_type VARCHAR(30),
    amount NUMERIC(14,2),
    net_value NUMERIC(14,2),
    due_date DATE,
    confirmed_at TIMESTAMP,
    received_at TIMESTAMP,
    invoice_url VARCHAR(500),
    receipt_url VARCHAR(500),
    description VARCHAR(500),
    last_provider_event_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tenant_billing_payments_tenant_due
    ON tenant_billing_payments (tenant_id, due_date DESC);
CREATE INDEX IF NOT EXISTS idx_tenant_billing_payments_subscription
    ON tenant_billing_payments (provider_subscription_id);
CREATE INDEX IF NOT EXISTS idx_tenant_billing_payments_status_due
    ON tenant_billing_payments (status, due_date);

CREATE TABLE IF NOT EXISTS asaas_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_event_id VARCHAR(150) NOT NULL UNIQUE,
    event_type VARCHAR(80) NOT NULL,
    provider_account_id VARCHAR(100),
    resource_type VARCHAR(30),
    resource_id VARCHAR(100),
    tenant_id UUID REFERENCES tenants(id) ON DELETE SET NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT NOW(),
    provider_created_at TIMESTAMP,
    received_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,
    last_error TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_asaas_webhook_events_pending
    ON asaas_webhook_events (status, next_attempt_at, received_at);
CREATE INDEX IF NOT EXISTS idx_asaas_webhook_events_tenant
    ON asaas_webhook_events (tenant_id, received_at DESC);

CREATE TABLE IF NOT EXISTS billing_provider_operations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    operation_type VARCHAR(50) NOT NULL,
    provider_resource_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(220) NOT NULL UNIQUE,
    payload JSONB,
    status VARCHAR(30) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_billing_provider_operations_pending
    ON billing_provider_operations (status, next_attempt_at, created_at);
