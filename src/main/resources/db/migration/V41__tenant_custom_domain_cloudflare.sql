ALTER TABLE tenant_website_config
    ADD COLUMN IF NOT EXISTS custom_domain_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS custom_domain_provider_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS custom_domain_target VARCHAR(255);
