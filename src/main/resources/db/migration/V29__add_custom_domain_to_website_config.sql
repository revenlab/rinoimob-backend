-- Add custom domain support to tenant website configuration
ALTER TABLE tenant_website_config
ADD COLUMN custom_domain VARCHAR(255) UNIQUE NULL;

-- Add index for quick domain lookups
CREATE INDEX idx_custom_domain ON tenant_website_config(custom_domain) WHERE custom_domain IS NOT NULL;
