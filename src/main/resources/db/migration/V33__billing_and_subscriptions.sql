-- Billing plans and tenant subscriptions

ALTER TABLE billing_plans
    ALTER COLUMN tenant_id DROP NOT NULL;

ALTER TABLE billing_plans
    ADD COLUMN IF NOT EXISTS code VARCHAR(30),
    ADD COLUMN IF NOT EXISTS max_leads_per_month INT,
    ADD COLUMN IF NOT EXISTS max_whatsapp_numbers INT,
    ADD COLUMN IF NOT EXISTS blog_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS custom_domain_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS automation_crm_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS public_api_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS vip_support_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS custom_implementation_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS ux_billing_plans_code ON billing_plans (code);

CREATE TABLE IF NOT EXISTS tenant_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    billing_plan_id UUID NOT NULL REFERENCES billing_plans(id),
    status VARCHAR(20) NOT NULL,
    provider VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    provider_customer_id VARCHAR(100),
    provider_subscription_id VARCHAR(100),
    provider_checkout_id VARCHAR(100),
    current_period_start TIMESTAMP,
    current_period_end TIMESTAMP,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tenant_subscriptions_tenant_id ON tenant_subscriptions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_tenant_subscriptions_billing_plan_id ON tenant_subscriptions (billing_plan_id);
CREATE INDEX IF NOT EXISTS idx_tenant_subscriptions_status ON tenant_subscriptions (status);

CREATE TABLE IF NOT EXISTS tenant_billing_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    billing_plan_id UUID NOT NULL REFERENCES billing_plans(id),
    max_users INT NOT NULL,
    max_properties INT NOT NULL,
    max_leads_per_month INT NOT NULL,
    max_whatsapp_numbers INT NOT NULL,
    blog_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    custom_domain_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    automation_crm_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    public_api_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    vip_support_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    custom_implementation_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT,
    updated_by_user_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tenant_billing_profiles_tenant_id ON tenant_billing_profiles (tenant_id);
CREATE INDEX IF NOT EXISTS idx_tenant_billing_profiles_billing_plan_id ON tenant_billing_profiles (billing_plan_id);

INSERT INTO billing_plans (
    code, plan_name, monthly_price, annual_price, max_users, max_properties, max_leads_per_month,
    max_whatsapp_numbers, blog_enabled, custom_domain_enabled, automation_crm_enabled,
    public_api_enabled, vip_support_enabled, custom_implementation_enabled, features, sort_order, active
)
VALUES
    ('FREE', 'Free', 0, NULL, 1, 10, 20, 1, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE,
     '1 usuário, 10 imóveis, 20 leads/mês, 1 WhatsApp, sem blog, sem domínio custom, sem automações, sem API pública, sem suporte VIP', 1, TRUE),
    ('STARTER', 'Starter', 0, NULL, 5, 100, 500, 5, TRUE, TRUE, FALSE, FALSE, FALSE, FALSE,
     '5 usuários, 100 imóveis, 500 leads/mês, blog, domínio custom, 5 WhatsApps', 2, TRUE),
    ('PRIME', 'Prime', 0, NULL, 15, 500, 2000, 15, TRUE, TRUE, TRUE, TRUE, TRUE, FALSE,
     '15 usuários, 500 imóveis, 2.000 leads/mês, blog, domínio custom, automações CRM, API pública, suporte VIP, 15 WhatsApps', 3, TRUE),
    ('ULTIMATE', 'Ultimate', 0, NULL, NULL, NULL, NULL, NULL, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE,
     'Ilimitado em usuários, imóveis, leads e WhatsApp; inclui blog, domínio custom, automações CRM, API pública, suporte VIP e implantação personalizada', 4, TRUE)
ON CONFLICT (code) DO NOTHING;
