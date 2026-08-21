ALTER TABLE tenant_website_config
    ADD COLUMN IF NOT EXISTS services_items TEXT;
