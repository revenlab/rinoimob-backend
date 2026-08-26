ALTER TABLE tenant_billing_profiles
    ADD COLUMN IF NOT EXISTS billing_cpf_cnpj VARCHAR(14),
    ADD COLUMN IF NOT EXISTS billing_phone VARCHAR(20),
    ADD COLUMN IF NOT EXISTS billing_address VARCHAR(255),
    ADD COLUMN IF NOT EXISTS billing_address_number VARCHAR(30),
    ADD COLUMN IF NOT EXISTS billing_address_complement VARCHAR(255),
    ADD COLUMN IF NOT EXISTS billing_postal_code VARCHAR(8),
    ADD COLUMN IF NOT EXISTS billing_province VARCHAR(120);
