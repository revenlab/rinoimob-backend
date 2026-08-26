ALTER TABLE tenant_subscriptions
    ADD COLUMN IF NOT EXISTS provider_invoice_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS payment_due_date TIMESTAMP;
