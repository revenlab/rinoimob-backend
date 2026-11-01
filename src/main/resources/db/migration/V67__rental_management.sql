ALTER TABLE properties DROP CONSTRAINT IF EXISTS properties_status_check;

CREATE TABLE rental_asaas_accounts (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    provider_account_id VARCHAR(100) UNIQUE,
    wallet_id VARCHAR(100),
    encrypted_api_key TEXT,
    webhook_token_hash VARCHAR(64),
    status VARCHAR(30) NOT NULL DEFAULT 'NOT_CONFIGURED',
    rino_fee_percent NUMERIC(7,4) NOT NULL DEFAULT 1.0000,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE rental_parties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    cpf_cnpj VARCHAR(14),
    email VARCHAR(200),
    phone VARCHAR(30),
    address TEXT,
    pix_key VARCHAR(255),
    pix_key_type VARCHAR(20),
    provider_customer_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);
CREATE UNIQUE INDEX ux_rental_parties_tenant_document ON rental_parties(tenant_id, cpf_cnpj)
    WHERE cpf_cnpj IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_rental_parties_tenant_name ON rental_parties(tenant_id, name);

CREATE TABLE rental_contracts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    property_id UUID NOT NULL REFERENCES properties(id),
    source_lead_id UUID REFERENCES leads(id),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    due_day INTEGER NOT NULL,
    charge_lead_days INTEGER NOT NULL DEFAULT 10,
    payout_day INTEGER NOT NULL,
    billing_type VARCHAR(20) NOT NULL DEFAULT 'BOLETO',
    base_rent NUMERIC(14,2) NOT NULL,
    condo_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    iptu_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    insurance_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    management_fee_type VARCHAR(20) NOT NULL DEFAULT 'PERCENTAGE',
    management_fee_value NUMERIC(14,4) NOT NULL DEFAULT 0,
    late_fee_percent NUMERIC(7,4) NOT NULL DEFAULT 2,
    monthly_interest_percent NUMERIC(7,4) NOT NULL DEFAULT 1,
    guarantee_type VARCHAR(30) NOT NULL DEFAULT 'NONE',
    guarantee_details TEXT,
    adjustment_index VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    next_adjustment_date DATE,
    asaas_customer_id VARCHAR(100),
    activated_at TIMESTAMP,
    ended_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP,
    CONSTRAINT ck_rental_contract_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_rental_contract_due_day CHECK (due_day BETWEEN 1 AND 28),
    CONSTRAINT ck_rental_contract_payout_day CHECK (payout_day BETWEEN 1 AND 28)
);
CREATE UNIQUE INDEX ux_rental_active_property ON rental_contracts(tenant_id, property_id)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;
CREATE INDEX idx_rental_contracts_tenant_status ON rental_contracts(tenant_id, status);

CREATE TABLE rental_contract_parties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    contract_id UUID NOT NULL REFERENCES rental_contracts(id) ON DELETE CASCADE,
    party_id UUID NOT NULL REFERENCES rental_parties(id),
    role VARCHAR(20) NOT NULL,
    ownership_percent NUMERIC(7,4),
    billing_responsible BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(contract_id, party_id, role)
);
CREATE INDEX idx_rental_contract_parties_contract ON rental_contract_parties(tenant_id, contract_id);

CREATE TABLE rental_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    contract_id UUID NOT NULL REFERENCES rental_contracts(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    file_fid VARCHAR(255) NOT NULL,
    file_url VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE rental_charges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    contract_id UUID NOT NULL REFERENCES rental_contracts(id),
    competence DATE NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    total_amount NUMERIC(14,2) NOT NULL,
    base_rent_amount NUMERIC(14,2) NOT NULL,
    rino_fee_percent NUMERIC(7,4) NOT NULL,
    rino_fee_amount NUMERIC(14,2) NOT NULL,
    provider_payment_id VARCHAR(100),
    provider_customer_id VARCHAR(100),
    invoice_url VARCHAR(1000),
    receipt_url VARCHAR(1000),
    billing_type VARCHAR(20),
    received_at TIMESTAMP,
    last_provider_event_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, contract_id, competence)
);
CREATE UNIQUE INDEX ux_rental_charge_provider ON rental_charges(tenant_id, provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;
CREATE INDEX idx_rental_charges_tenant_due ON rental_charges(tenant_id, due_date, status);

CREATE TABLE rental_charge_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    charge_id UUID NOT NULL REFERENCES rental_charges(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL,
    description VARCHAR(200) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    payout_treatment VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE rental_payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    contract_id UUID NOT NULL REFERENCES rental_contracts(id),
    charge_id UUID NOT NULL REFERENCES rental_charges(id),
    owner_party_id UUID NOT NULL REFERENCES rental_parties(id),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    gross_owner_amount NUMERIC(14,2) NOT NULL,
    management_fee_amount NUMERIC(14,2) NOT NULL,
    net_amount NUMERIC(14,2) NOT NULL,
    scheduled_date DATE,
    provider_transfer_id VARCHAR(100),
    approved_by_user_id UUID REFERENCES users(id),
    approved_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, charge_id, owner_party_id)
);
CREATE UNIQUE INDEX ux_rental_payout_provider ON rental_payouts(tenant_id, provider_transfer_id)
    WHERE provider_transfer_id IS NOT NULL;
